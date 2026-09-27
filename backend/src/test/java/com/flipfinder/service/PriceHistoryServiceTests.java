package com.flipfinder.service;

import static com.flipfinder.repository.PriceHistoryRepository.FIVE_MINUTES;
import static com.flipfinder.repository.PriceHistoryRepository.HOUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipfinder.dto.ItemDto;
import com.flipfinder.repository.ItemRepository;
import com.flipfinder.repository.PriceHistoryRepository;
import com.flipfinder.repository.PriceHistoryRepository.Row;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PriceHistoryServiceTests {
    private static final long NOON = 1_789_948_800L + 12 * 3600;
    // The 12:00 five-minute bucket ended two and a half minutes ago, so the wiki has published it.
    private static final long NOW = NOON + 450;

    private final PriceHistoryRepository repository = mock(PriceHistoryRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final WikiPriceClient wiki = mock(WikiPriceClient.class);
    private final List<String> requested = new ArrayList<>();

    private final Set<String> stored = new HashSet<>();

    /** A service with no waits, whose repository remembers which buckets were saved. */
    private PriceHistoryService service(int fiveMinuteBackfillHours, int hourlyDays, int requestsPerRun)
            throws Exception {
        when(repository.importedBuckets(anyInt(), anyLong())).thenAnswer(invocation -> {
            int step = invocation.getArgument(0);
            Set<Long> buckets = new HashSet<>();
            for (String key : stored) {
                String[] parts = key.split(":");
                if (Integer.parseInt(parts[0]) == step) {
                    buckets.add(Long.parseLong(parts[1]));
                }
            }
            return buckets;
        });
        doAnswer(invocation -> stored.add(invocation.getArgument(0) + ":" + invocation.getArgument(1)))
                .when(repository).saveBucket(anyInt(), anyLong(), anyList());
        when(wiki.get(anyString())).thenAnswer(invocation -> {
            requested.add(invocation.getArgument(0));
            return new ObjectMapper().readTree("""
                    {"data": {"2": {"avgHighPrice": 285, "highPriceVolume": 12799, "avgLowPrice": null, "lowPriceVolume": 0}}}
                    """);
        });
        return new PriceHistoryService(repository, items, wiki, 7, hourlyDays, fiveMinuteBackfillHours,
                requestsPerRun, 0, 0);
    }

    @Test
    void fetchesRecentHistoryQuicklyThenTheRestGently() throws Exception {
        PriceHistoryService service = service(24, 3, 3);
        stored.add(FIVE_MINUTES + ":" + (NOON - 300));

        // Two hours of five-minute buckets less the stored one, two days of hourly ones, then three more.
        assertThat(service.catchUp(NOW)).isEqualTo(23 + 48 + 3);

        assertThat(requested.getFirst()).isEqualTo("/5m?timestamp=" + NOON);
        assertThat(requested).doesNotContain("/5m?timestamp=" + (NOON - 300));
        // The 11:00 hour is the newest the wiki has published.
        assertThat(requested.get(23)).isEqualTo("/1h?timestamp=" + (NOON - 3600));
        assertThat(requested.get(70)).isEqualTo("/1h?timestamp=" + (NOON - 48 * 3600));
        assertThat(requested.subList(71, 74)).containsExactly(
                "/5m?timestamp=" + (NOON - 7200),
                "/5m?timestamp=" + (NOON - 7500),
                "/5m?timestamp=" + (NOON - 7800));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Row>> rows = ArgumentCaptor.forClass(List.class);
        verify(repository).saveBucket(eq(FIVE_MINUTES), eq(NOON), rows.capture());
        assertThat(rows.getValue()).containsExactly(new Row(2, 285L, 12_799, null, 0));
    }

    @Test
    void movesOnToOlderHourlyBucketsOnceRecentOnesAreStored() throws Exception {
        PriceHistoryService service = service(24, 3, 2);
        for (long bucket = NOON; bucket > NOON - 86_400; bucket -= 300) {
            stored.add(FIVE_MINUTES + ":" + bucket);
        }
        for (long bucket = NOON - 3600; bucket > NOON - 49 * 3600; bucket -= 3600) {
            stored.add(HOUR + ":" + bucket);
        }

        service.catchUp(NOW);

        assertThat(requested).containsExactly(
                "/1h?timestamp=" + (NOON - 49 * 3600), "/1h?timestamp=" + (NOON - 50 * 3600));
    }

    @Test
    void waitsForARecentBucketThatIsNotPublishedYet() throws Exception {
        PriceHistoryService service = service(1, 1, 20);
        when(wiki.get(anyString())).thenReturn(new ObjectMapper().readTree("{\"data\": {}}"));

        service.catchUp(NOW);

        // A recent empty bucket may still be coming; an older empty one is a gap in the wiki's data.
        verify(repository, never()).saveBucket(eq(FIVE_MINUTES), anyLong(), anyList());
        verify(repository, never()).saveBucket(HOUR, NOON - 3600, List.of());
        verify(repository).saveBucket(HOUR, NOON - 7200, List.of());
    }

    @Test
    void findsAnExactNameBeforeBusierPartialMatches() {
        ItemDto leaf = item(1, "Gold leaf", 5);
        ItemDto bar = item(2, "Gold bar", 1_000);
        ItemDto goldLeafBoots = item(3, "Gold leaf boots", 50);
        when(items.findAll()).thenReturn(List.of(bar, goldLeafBoots, leaf));
        PriceHistoryService service = new PriceHistoryService(repository, items, wiki, 7, 30, 24, 20, 0, 0);

        assertThat(service.find("gold leaf").orElseThrow().item()).isSameAs(leaf);
        PriceHistoryService.Match gold = service.find("gold").orElseThrow();
        assertThat(gold.item()).isSameAs(bar);
        assertThat(gold.others()).containsExactly("Gold leaf boots", "Gold leaf");
        assertThat(service.find("dragon")).isEmpty();
    }

    private static ItemDto item(int id, String name, long volume) {
        ItemDto item = new ItemDto();
        item.id = id;
        item.name = name;
        item.highPriceVolume5m = volume;
        return item;
    }
}
