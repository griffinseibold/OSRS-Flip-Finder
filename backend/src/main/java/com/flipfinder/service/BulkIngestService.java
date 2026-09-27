package com.flipfinder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class BulkIngestService {
    private static final Logger logger = LoggerFactory.getLogger(BulkIngestService.class);
    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;
    private final String baseUrl;
    private final String userAgent;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public BulkIngestService(
            JdbcTemplate jdbc,
            TransactionOperations transactions,
            @Value("${flipfinder.ingest.base-url}") String baseUrl,
            @Value("${flipfinder.ingest.user-agent}") String userAgent) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.baseUrl = baseUrl.replaceFirst("/+$", "");
        this.userAgent = userAgent;
    }

    public void fetchAndProcess() {
        try {
            JsonNode mapping = fetchJson("/mapping");
            JsonNode latest = fetchJson("/latest");
            JsonNode fiveMinute = fetchJson("/5m");

            long started = System.nanoTime();
            IngestResult result = processResponses(mapping, latest, fiveMinute);
            logger.info(
                    "Imported {} mappings, {} latest prices and {} five-minute prices from RuneScape Wiki API in {} ms",
                    result.mappedItems(), result.latestPrices(), result.fiveMinutePrices(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("RuneScape Wiki price ingest interrupted", e);
        } catch (Exception e) {
            logger.error("RuneScape Wiki price ingest failed", e);
        }
    }

    private JsonNode fetchJson(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IllegalStateException(path + " returned HTTP " + response.statusCode());
            }
            return mapper.readTree(body);
        }
    }

    IngestResult processResponses(JsonNode mapping, JsonNode latest, JsonNode fiveMinute) {
        if (!mapping.isArray()) {
            throw new IllegalStateException("Expected /mapping to return an array");
        }

        JsonNode latestData = latest.path("data");
        if (!latestData.isObject()) {
            throw new IllegalStateException("Expected /latest.data to be an object");
        }

        JsonNode fiveMinuteData = fiveMinute.path("data");
        if (!fiveMinuteData.isObject() || !fiveMinute.hasNonNull("timestamp")) {
            throw new IllegalStateException("Expected /5m to return timestamp and data fields");
        }

        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        // Write the whole import in one transaction. In auto-commit mode SQLite
        // commits every row separately, and on disk each commit waits for fsync,
        // which made an import take minutes. Readers also never see a partial import.
        return transactions.execute(status -> {
            int mappedItems = processMapping(mapping, now);
            int latestPrices = processLatest(latestData, now);
            int fiveMinutePrices = processFiveMinute(
                    fiveMinuteData,
                    fiveMinute.get("timestamp").asLong(),
                    now);
            return new IngestResult(mappedItems, latestPrices, fiveMinutePrices);
        });
    }

    private int processMapping(JsonNode mapping, String now) {
        final String sql = """
                INSERT INTO items (
                  id, name, examine, members, low_alchemy, high_alchemy,
                  buy_limit, value, icon, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                  name = excluded.name,
                  examine = excluded.examine,
                  members = excluded.members,
                  low_alchemy = excluded.low_alchemy,
                  high_alchemy = excluded.high_alchemy,
                  buy_limit = excluded.buy_limit,
                  value = excluded.value,
                  icon = excluded.icon,
                  updated_at = excluded.updated_at
                """;
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        int count = 0;

        for (JsonNode item : mapping) {
            if (!item.isObject() || !item.hasNonNull("id") || !item.hasNonNull("name")) {
                continue;
            }
            batch.add(new Object[] {
                    item.get("id").asInt(),
                    item.get("name").asText(),
                    textOrNull(item, "examine"),
                    item.path("members").asBoolean(false) ? 1 : 0,
                    longOrNull(item, "lowalch"),
                    longOrNull(item, "highalch"),
                    integerOrNull(item, "limit"),
                    longOrNull(item, "value"),
                    textOrNull(item, "icon"),
                    now
            });
            count++;
            flushWhenFull(sql, batch);
        }
        flush(sql, batch);
        return count;
    }

    private int processLatest(JsonNode latestData, String now) {
        final String sql = """
                UPDATE items SET
                  high_price = ?,
                  high_price_time = ?,
                  low_price = ?,
                  low_price_time = ?,
                  updated_at = ?
                WHERE id = ?
                """;
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        Iterator<Map.Entry<String, JsonNode>> fields = latestData.properties().iterator();
        int count = 0;

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode price = entry.getValue();
            if (!price.isObject()) {
                continue;
            }
            batch.add(new Object[] {
                    longOrNull(price, "high"),
                    longOrNull(price, "highTime"),
                    longOrNull(price, "low"),
                    longOrNull(price, "lowTime"),
                    now,
                    Integer.parseInt(entry.getKey())
            });
            count++;
            flushWhenFull(sql, batch);
        }
        flush(sql, batch);
        return count;
    }

    private int processFiveMinute(JsonNode fiveMinuteData, long timestamp, String now) {
        final String sql = """
                UPDATE items SET
                  average_high_price_5m = ?,
                  high_price_volume_5m = ?,
                  average_low_price_5m = ?,
                  low_price_volume_5m = ?,
                  five_minute_timestamp = ?,
                  updated_at = ?
                WHERE id = ?
                """;

        jdbc.update("""
                UPDATE items SET
                  average_high_price_5m = NULL,
                  high_price_volume_5m = NULL,
                  average_low_price_5m = NULL,
                  low_price_volume_5m = NULL,
                  five_minute_timestamp = ?,
                  updated_at = ?
                """, timestamp, now);

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        Iterator<Map.Entry<String, JsonNode>> fields = fiveMinuteData.properties().iterator();
        int count = 0;

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode price = entry.getValue();
            if (!price.isObject()) {
                continue;
            }
            batch.add(new Object[] {
                    doubleOrNull(price, "avgHighPrice"),
                    longOrNull(price, "highPriceVolume"),
                    doubleOrNull(price, "avgLowPrice"),
                    longOrNull(price, "lowPriceVolume"),
                    timestamp,
                    now,
                    Integer.parseInt(entry.getKey())
            });
            count++;
            flushWhenFull(sql, batch);
        }
        flush(sql, batch);
        return count;
    }

    private void flushWhenFull(String sql, List<Object[]> batch) {
        if (batch.size() == BATCH_SIZE) {
            flush(sql, batch);
        }
    }

    private void flush(String sql, List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(sql, List.copyOf(batch));
        batch.clear();
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asLong() : null;
    }

    private static Integer integerOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asDouble() : null;
    }

    record IngestResult(int mappedItems, int latestPrices, int fiveMinutePrices) {
    }
}
