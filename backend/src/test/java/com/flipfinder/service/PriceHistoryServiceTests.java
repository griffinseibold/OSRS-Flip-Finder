package com.flipfinder.service;

import static com.flipfinder.repository.PriceHistoryRepository.FIVE_MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private PriceHistoryService service(int requestsPerRun) throws Exception {
        when(repository.importedBuckets(anyInt(), anyLong())).thenReturn(Set.of());
        when(wiki.get(anyString())).thenAnswer(invocation -> {
            requested.add(invocation.getArgument(0));
            return new ObjectMapper().readTree("""
                    {"data": {"2": {"avgHighPrice": 285, "highPriceVolume": 12799, "avgLowPrice": null, "lowPriceVolume": 0}}}
                    """);
        });
        // One day of five-minute buckets and two days of hourly ones, with no wait between requests.
        return new PriceHistoryService(repository, items, wiki, 7, 2, 24, requestsPerRun, 0);
    }

    @Test
    void importsTheNewestMissingBucketsFirstAndAFewAtATime() throws Exception {
        PriceHistoryService service = service(3);
        when(repository.importedBuckets(eq(FIVE_MINUTES), anyLong())).thenReturn(Set.of(NOON - 300));

        assertThat(service.catchUp(NOW)).isEqualTo(3);

        // 11:55 is already stored.
        assertThat(requested).containsExactly(
                "/5m?timestamp=" + NOON,
                "/5m?timestamp=" + (NOON - 600),
                "/5m?timestamp=" + (NOON - 900));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Row>> rows = ArgumentCaptor.forClass(List.class);
        verify(repository).saveBucket(eq(FIVE_MINUTES), eq(NOON), rows.capture());
        assertThat(rows.getValue()).containsExactly(new Row(2, 285L, 12_799, null, 0));
    }

    @Test
    void movesOnToHourlyBucketsOnceFiveMinuteOnesAreStored() throws Exception {
        PriceHistoryService service = service(2);
        Set<Long> day = new HashSet<>();
        for (long bucket = NOON; bucket > NOON - 86_400; bucket -= 300) {
            day.add(bucket);
        }
        when(repository.importedBuckets(eq(FIVE_MINUTES), anyLong())).thenReturn(day);

        service.catchUp(NOW);

        // The 11:00 hour is the newest the wiki has published.
        assertThat(requested).containsExactly("/1h?timestamp=" + (NOON - 3600), "/1h?timestamp=" + (NOON - 7200));
    }

    @Test
    void waitsForARecentBucketThatIsNotPublishedYet() throws Exception {
        PriceHistoryService service = service(1);
        when(wiki.get(anyString())).thenReturn(new ObjectMapper().readTree("{\"data\": {}}"));

        service.catchUp(NOW);

        verify(repository, never()).saveBucket(anyInt(), anyLong(), anyList());
    }

    @Test
    void findsAnExactNameBeforeBusierPartialMatches() {
        ItemDto leaf = item(1, "Gold leaf", 5);
        ItemDto bar = item(2, "Gold bar", 1_000);
        ItemDto goldLeafBoots = item(3, "Gold leaf boots", 50);
        when(items.findAll()).thenReturn(List.of(bar, goldLeafBoots, leaf));
        PriceHistoryService service = new PriceHistoryService(repository, items, wiki, 7, 30, 24, 20, 0);

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
