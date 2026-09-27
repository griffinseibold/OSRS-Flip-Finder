package com.flipfinder.service;

import static com.flipfinder.service.FlipCalculatorTests.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import com.flipfinder.dto.FlipDto;
import com.flipfinder.dto.FlipPageResponse;
import com.flipfinder.dto.ItemDto;
import com.flipfinder.repository.ItemRepository;
import com.flipfinder.service.FlipQuery.Direction;
import com.flipfinder.service.FlipQuery.Membership;
import com.flipfinder.service.FlipQuery.PriceBasis;
import com.flipfinder.service.FlipQuery.Sort;

import org.junit.jupiter.api.Test;

class FlipServiceTests {
    private final ItemRepository repository = mock(ItemRepository.class);
    private final FlipService service = new FlipService(repository);

    private static ItemDto item(int id, String name, Consumer<ItemDto> changes) {
        ItemDto item = FlipCalculatorTests.item();
        item.id = id;
        item.name = name;
        changes.accept(item);
        return item;
    }

    private static FlipQuery query(Sort sort, Direction direction) {
        return new FlipQuery(PriceBasis.LATEST, "", Membership.ALL, 0, 0, null, sort, direction);
    }

    private List<String> names(FlipQuery query) {
        return service.find(query, 0, 50, NOW).items.stream().map(flip -> flip.item.name).toList();
    }

    @Test
    void ranksByEstimatedProfitByDefault() {
        when(repository.findAll()).thenReturn(List.of(
                // Big limit but only 1 trade per five minutes: 48 fill, 80 × 48 = 3,840.
                item(1, "Thin", item -> {
                    item.buyLimit = 10_000;
                    item.highPriceVolume5m = 1L;
                    item.lowPriceVolume5m = 1L;
                }),
                // Smaller margin but trading fast: 41 × 1,000 = 41,000.
                item(2, "Liquid", item -> {
                    item.buyLimit = 1_000;
                    item.highPrice = 960L;
                    item.highPriceVolume5m = 500L;
                    item.lowPriceVolume5m = 500L;
                }),
                item(3, "No trades", item -> item.highPrice = null),
                // A huge margin and volume, but no known buy limit: no estimate, so it ranks last.
                item(4, "No limit", item -> {
                    item.buyLimit = null;
                    item.highPrice = 5_000L;
                    item.highPriceVolume5m = 10_000L;
                    item.lowPriceVolume5m = 10_000L;
                })));

        FlipPageResponse page = service.find(new FlipQuery(null, null, null, 0, 0, null, null, null), 0, 50, NOW);

        assertThat(page.items).extracting(flip -> flip.item.name)
                .containsExactly("Liquid", "Thin", "No limit", "No trades");
        assertThat(page.topFlip.item.name).isEqualTo("Liquid");
        assertThat(page.profitable).isEqualTo(3);
        assertThat(page.losing).isZero();
        assertThat(page.itemCount).isEqualTo(4);
        assertThat(page.pricesAsOf).isEqualTo(NOW - 60);
    }

    @Test
    void sortsEitherWayWithMissingValuesLast() {
        when(repository.findAll()).thenReturn(List.of(
                item(1, "B", item -> {}),
                item(2, "A", item -> item.highPrice = 2_000L),
                item(3, "C", item -> item.highPrice = null),
                item(4, "D", item -> item.lowPrice = 1_000L)));

        assertThat(names(query(Sort.MARGIN, Direction.DESC))).containsExactly("A", "B", "D", "C");
        assertThat(names(query(Sort.MARGIN, Direction.ASC))).containsExactly("D", "B", "A", "C");
        assertThat(names(query(Sort.NAME, null))).containsExactly("A", "B", "C", "D");
        // Equal buy limits fall back to name order.
        assertThat(names(query(Sort.BUY_LIMIT, null))).containsExactly("A", "B", "C", "D");
    }

    @Test
    void filtersBySearchMembershipTradeAgeVolumeAndBudget() {
        when(repository.findAll()).thenReturn(List.of(
                item(1, "Gold leaf", item -> {}),
                item(2, "Gold bar", item -> item.members = false),
                item(3, "Old gold", item -> item.lowPriceTime = NOW - 2 * 3600),
                item(4, "Quiet gold", item -> {
                    item.highPriceVolume5m = 5L;
                    item.lowPriceVolume5m = null;
                }),
                item(5, "Dear gold", item -> item.lowPrice = 50_000L),
                item(6, "Rune bar", item -> {})));

        FlipQuery query = new FlipQuery(PriceBasis.LATEST, " GOLD ", Membership.MEMBERS, 60, 10, 10_000L, null, null);
        FlipPageResponse page = service.find(query, 0, 50, NOW);

        assertThat(page.items).extracting(flip -> flip.item.name).containsExactly("Gold leaf");
        assertThat(page.searchMatches).isEqualTo(5);
    }

    @Test
    void budgetHidesItemsItCannotAffordEvenWithoutABuyPrice() {
        when(repository.findAll()).thenReturn(List.of(
                item(1, "Nine million", item -> {
                    item.lowPrice = 9_000_000L;
                    item.highPrice = 9_500_000L;
                    item.averageLowPrice5m = 9_000_000.0;
                    item.averageHighPrice5m = 9_500_000.0;
                }),
                item(2, "Twelve million", item -> {
                    item.lowPrice = 12_000_000L;
                    item.highPrice = 12_500_000L;
                    item.averageLowPrice5m = 12_000_000.0;
                    item.averageHighPrice5m = 12_500_000.0;
                }),
                // No five-minute trades, so no buy price when priced from averages.
                item(3, "Quiet twelve million", item -> {
                    item.lowPrice = 12_000_000L;
                    item.highPrice = 12_500_000L;
                    item.averageLowPrice5m = null;
                    item.averageHighPrice5m = null;
                }),
                // Never traded at the low price; the high price shows it is too dear.
                item(4, "Only a high price", item -> {
                    item.lowPrice = null;
                    item.highPrice = 12_500_000L;
                    item.averageLowPrice5m = null;
                    item.averageHighPrice5m = null;
                }),
                item(5, "Never traded", item -> {
                    item.lowPrice = null;
                    item.highPrice = null;
                    item.averageLowPrice5m = null;
                    item.averageHighPrice5m = null;
                })));

        for (PriceBasis basis : PriceBasis.values()) {
            FlipQuery query = new FlipQuery(basis, "", Membership.ALL, 0, 0, 10_000_000L, null, null);

            assertThat(service.find(query, 0, 50, NOW).items)
                    .extracting(flip -> flip.item.name)
                    .as("priced from %s", basis)
                    .containsExactly("Nine million");
        }
    }

    @Test
    void ignoresTradeAgeForFiveMinuteAverages() {
        when(repository.findAll()).thenReturn(List.of(item(1, "Old", item -> item.lowPriceTime = NOW - 2 * 3600)));

        FlipQuery query = new FlipQuery(PriceBasis.AVERAGE_5M, "", Membership.ALL, 60, 0, null, null, null);

        assertThat(service.find(query, 0, 50, NOW).items).hasSize(1);
    }

    @Test
    void pagesAndClampsOutOfRangePages() {
        when(repository.findAll()).thenReturn(List.of(
                item(1, "A", item -> {}), item(2, "B", item -> {}), item(3, "C", item -> {})));

        FlipPageResponse page = service.find(query(Sort.NAME, null), 7, 2, NOW);

        assertThat(page.page).isEqualTo(1);
        assertThat(page.totalPages).isEqualTo(2);
        assertThat(page.total).isEqualTo(3);
        assertThat(page.items).extracting((FlipDto flip) -> flip.item.name).containsExactly("C");
    }
}
