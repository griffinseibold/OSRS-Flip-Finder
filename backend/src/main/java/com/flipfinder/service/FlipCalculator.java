package com.flipfinder.service;

import com.flipfinder.dto.FlipDto;
import com.flipfinder.dto.FlipDto.LimitedBy;
import com.flipfinder.dto.ItemDto;
import com.flipfinder.service.FlipQuery.PriceBasis;

import java.util.Set;

/** Prices an item as a flip. */
public final class FlipCalculator {
    // Grand Exchange tax, per https://oldschool.runescape.wiki/w/Grand_Exchange:
    // the seller pays 2% of the sale price, rounded down and capped per item.
    // Sales under 50 coins therefore pay nothing.
    static final long TAX_PERCENT = 2;
    static final long TAX_CAP = 5_000_000;

    static final Set<Integer> TAX_EXEMPT_ITEM_IDS = Set.of(
            13190, // Old school bond
            3008, 3010, 3012, 3014, // Energy potion(4) to (1)
            882, 884, 886, // Bronze, iron and steel arrows
            806, 807, 808, // Bronze, iron and steel darts
            558, // Mind rune
            365, 2309, 1891, 2140, 2142, 347, 379, 355, 2327, 351, 329, 315, 361, // Low-level food
            8007, 8008, 8009, 8010, 8011, 8013, 28790, 28824, // Teleport tablets
            3853, // Games necklace(8)
            2552, // Ring of dueling(8)
            233, 952, 1733, 1735, 1755, 1785, 2347, 5325, 5329, 5331, 5341, 5343, 8794); // Tools

    // Buy limits reset every four hours, which is 48 five-minute windows.
    static final long WINDOWS_PER_BUY_LIMIT = 48;

    private FlipCalculator() {
    }

    public static long tax(int itemId, long sellPrice) {
        if (TAX_EXEMPT_ITEM_IDS.contains(itemId)) {
            return 0;
        }
        return Math.min(sellPrice * TAX_PERCENT / 100, TAX_CAP);
    }

    public static FlipDto calculate(ItemDto item, PriceBasis basis, Long cashStack) {
        FlipDto flip = new FlipDto();
        flip.item = item;
        flip.buyPrice = basis == PriceBasis.LATEST ? item.lowPrice : round(item.averageLowPrice5m);
        flip.sellPrice = basis == PriceBasis.LATEST ? item.highPrice : round(item.averageHighPrice5m);
        flip.tax = flip.sellPrice == null ? null : tax(item.id, flip.sellPrice);
        if (flip.buyPrice != null && flip.sellPrice != null) {
            flip.margin = flip.sellPrice - flip.tax - flip.buyPrice;
            flip.roi = flip.buyPrice > 0 ? (double) flip.margin / flip.buyPrice : null;
        }

        // How many to buy: one buy limit, or fewer if the cash stack runs out first.
        Long limit = item.buyLimit == null ? null : item.buyLimit.longValue();
        Long affordable = cashStack == null || flip.buyPrice == null || flip.buyPrice <= 0
                ? null
                : cashStack / flip.buyPrice;
        boolean cashLimited = affordable != null && (limit == null || affordable < limit);
        flip.quantity = cashLimited ? affordable : limit;

        // How many could actually fill: buy offers fill from instant sells and
        // sell offers from instant buys, so the slower side sets the pace.
        if (item.highPriceVolume5m != null || item.lowPriceVolume5m != null) {
            flip.volume5m = orZero(item.highPriceVolume5m) + orZero(item.lowPriceVolume5m);
        }
        long pace = WINDOWS_PER_BUY_LIMIT * Math.min(orZero(item.highPriceVolume5m), orZero(item.lowPriceVolume5m));
        if (flip.quantity == null || pace < flip.quantity) {
            flip.fillableQuantity = pace;
            flip.limitedBy = LimitedBy.VOLUME;
        } else {
            flip.fillableQuantity = flip.quantity;
            flip.limitedBy = cashLimited ? LimitedBy.CASH_STACK : LimitedBy.BUY_LIMIT;
        }

        if (flip.margin != null) {
            flip.potentialProfit = flip.quantity == null ? null : flip.margin * flip.quantity;
            flip.estimatedProfit = flip.margin * flip.fillableQuantity;
        }
        if (item.highPriceTime != null && item.lowPriceTime != null) {
            flip.lastTradeTime = Math.min(item.highPriceTime, item.lowPriceTime);
        }
        return flip;
    }

    private static Long round(Double value) {
        return value == null ? null : Math.round(value);
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
