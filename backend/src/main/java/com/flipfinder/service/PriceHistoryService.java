package com.flipfinder.service;

import static com.flipfinder.repository.PriceHistoryRepository.FIVE_MINUTES;
import static com.flipfinder.repository.PriceHistoryRepository.HOUR;

import com.fasterxml.jackson.databind.JsonNode;
import com.flipfinder.dto.ItemDto;
import com.flipfinder.dto.ItemHistoryDto;
import com.flipfinder.repository.ItemRepository;
import com.flipfinder.repository.PriceHistoryRepository;
import com.flipfinder.repository.PriceHistoryRepository.Row;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Keeps five-minute and hourly trading history from the RuneScape Wiki and
 * compares each item's latest trading with it. The homelab profile turns it on.
 */
@Service
@ConditionalOnProperty(name = "flipfinder.history.enabled", havingValue = "true")
public class PriceHistoryService {
    private static final Logger logger = LoggerFactory.getLogger(PriceHistoryService.class);
    // The wiki publishes a bucket shortly after it ends.
    private static final long PUBLISH_DELAY_SECONDS = 120;
    private static final long DAY = 86_400;

    private final PriceHistoryRepository history;
    private final ItemRepository items;
    private final WikiPriceClient wiki;
    private final long fiveMinuteRetention;
    private final long hourlyRetention;
    private final long fiveMinuteBackfill;
    private final int requestsPerRun;
    private final long requestDelayMs;
    private long prunedAt;

    public PriceHistoryService(
            PriceHistoryRepository history,
            ItemRepository items,
            WikiPriceClient wiki,
            @Value("${flipfinder.history.five-minute-days:7}") int fiveMinuteDays,
            @Value("${flipfinder.history.hourly-days:30}") int hourlyDays,
            @Value("${flipfinder.history.five-minute-backfill-hours:24}") int fiveMinuteBackfillHours,
            @Value("${flipfinder.history.requests-per-run:20}") int requestsPerRun,
            @Value("${flipfinder.history.request-delay-ms:1500}") long requestDelayMs) {
        this.history = history;
        this.items = items;
        this.wiki = wiki;
        this.fiveMinuteRetention = fiveMinuteDays * DAY;
        this.hourlyRetention = hourlyDays * DAY;
        this.fiveMinuteBackfill = Math.min(fiveMinuteBackfillHours * 3600L, fiveMinuteRetention);
        this.requestsPerRun = requestsPerRun;
        this.requestDelayMs = requestDelayMs;
    }

    /**
     * Imports buckets missing from the history, newest first and a few at a
     * time, so a first start backfills gradually without hammering the wiki.
     * Recent five-minute buckets come first, since the latest trading matters most.
     *
     * @return how many buckets it requested
     */
    public int catchUp(long now) throws InterruptedException {
        if (now - prunedAt >= 3600) {
            history.prune(FIVE_MINUTES, now - fiveMinuteRetention);
            history.prune(HOUR, now - hourlyRetention);
            prunedAt = now;
        }
        try {
            int requested = fill(FIVE_MINUTES, "/5m", now, fiveMinuteBackfill, requestsPerRun);
            return requested + fill(HOUR, "/1h", now, hourlyRetention, requestsPerRun - requested);
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not import price history: {}", e.getMessage());
            return 0;
        }
    }

    private int fill(int step, String path, long now, long window, int budget)
            throws IOException, InterruptedException {
        long newest = Math.floorDiv(now - step - PUBLISH_DELAY_SECONDS, step) * step;
        long oldest = newest - window + step;
        Set<Long> imported = history.importedBuckets(step, oldest);
        int requested = 0;
        for (long bucket = newest; bucket >= oldest && requested < budget; bucket -= step) {
            if (imported.contains(bucket)) {
                continue;
            }
            if (requested > 0) {
                Thread.sleep(requestDelayMs);
            }
            JsonNode response = wiki.get(path + "?timestamp=" + bucket);
            requested++;
            List<Row> rows = rows(response.path("data"));
            // A recent empty bucket may not be published yet; an old one is a gap in the wiki's data.
            if (rows.isEmpty() && bucket > now - 3600 - step) {
                continue;
            }
            history.saveBucket(step, bucket, rows);
        }
        if (requested > 0) {
            logger.info("Imported {} {} price history buckets", requested, step == HOUR ? "hourly" : "five-minute");
        }
        return requested;
    }

    static List<Row> rows(JsonNode data) {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : data.properties()) {
            JsonNode bucket = entry.getValue();
            if (!bucket.isObject()) {
                continue;
            }
            rows.add(new Row(
                    Integer.parseInt(entry.getKey()),
                    bucket.hasNonNull("avgHighPrice") ? Math.round(bucket.get("avgHighPrice").asDouble()) : null,
                    bucket.path("highPriceVolume").asLong(0),
                    bucket.hasNonNull("avgLowPrice") ? Math.round(bucket.get("avgLowPrice").asDouble()) : null,
                    bucket.path("lowPriceVolume").asLong(0)));
        }
        return rows;
    }

    /** How the item's latest trading compares with its stored history. */
    public Optional<ItemHistoryDto> summarize(int itemId, long now) {
        return items.findById(itemId).map(item -> summarize(item, now));
    }

    ItemHistoryDto summarize(ItemDto item, long now) {
        long fiveMinuteSince = now - fiveMinuteRetention;
        long hourlySince = now - hourlyRetention;
        return TradingHistory.summarize(item,
                history.series(FIVE_MINUTES, item.id, fiveMinuteSince),
                history.tradedBuckets(FIVE_MINUTES, fiveMinuteSince),
                history.series(HOUR, item.id, hourlySince),
                history.tradedBuckets(HOUR, hourlySince));
    }

    /** An item looked up by name, and other items the name also matches. */
    public record Match(ItemDto item, List<String> others) {
    }

    /**
     * Finds the item a name refers to: an exact match, or else the most
     * actively traded item whose name contains it.
     */
    public Optional<Match> find(String name) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        List<ItemDto> matches = items.findAll().stream()
                .filter(item -> item.name.toLowerCase(Locale.ROOT).contains(wanted))
                .sorted(Comparator.<ItemDto, Boolean>comparing(item -> !item.name.equalsIgnoreCase(wanted))
                        .thenComparing(Comparator.comparingLong(PriceHistoryService::volume5m).reversed())
                        .thenComparing(item -> item.name.length()))
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        List<String> others = matches.stream().skip(1).limit(5).map(item -> item.name).toList();
        return Optional.of(new Match(matches.getFirst(), others));
    }

    private static long volume5m(ItemDto item) {
        return (item.highPriceVolume5m == null ? 0 : item.highPriceVolume5m)
                + (item.lowPriceVolume5m == null ? 0 : item.lowPriceVolume5m);
    }
}
