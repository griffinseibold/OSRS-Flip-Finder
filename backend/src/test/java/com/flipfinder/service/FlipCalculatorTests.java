package com.flipfinder.service;

import com.flipfinder.dto.FlipDto;
import com.flipfinder.dto.FlipDto.LimitedBy;
import com.flipfinder.dto.ItemDto;
import com.flipfinder.service.FlipQuery.PriceBasis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FlipCalculatorTests {
    static final long NOW = 1_790_525_000L;

    /** Buys at 900, sells at 1,000 (20 tax), limit 100, 12 instant buys and 8 instant sells in five minutes. */
    static ItemDto item() {
        ItemDto item = new ItemDto();
        item.id = 1;
        item.name = "Gold leaf";
        item.members = true;
        item.buyLimit = 100;
        item.highPrice = 1_000L;
        item.highPriceTime = NOW - 60;
        item.lowPrice = 900L;
        item.lowPriceTime = NOW - 120;
        item.averageHighPrice5m = 990.4;
        item.highPriceVolume5m = 12L;
        item.averageLowPrice5m = 910.6;
        item.lowPriceVolume5m = 8L;
        item.updatedAt = "2026-09-27T12:00:00-04:00";
        return item;
    }

    @Test
    void taxIsTwoPercentRoundedDown() {
        assertThat(FlipCalculator.tax(1, 1_000)).isEqualTo(20);
        assertThat(FlipCalculator.tax(1, 149)).isEqualTo(2);
    }

    @Test
    void salesUnderFiftyCoinsPayNoTax() {
        assertThat(FlipCalculator.tax(1, 49)).isZero();
        assertThat(FlipCalculator.tax(1, 50)).isEqualTo(1);
    }

    @Test
    void taxIsCappedAtFiveMillionPerItem() {
        assertThat(FlipCalculator.tax(1, 250_000_000)).isEqualTo(5_000_000);
        assertThat(FlipCalculator.tax(1, 2_000_000_000)).isEqualTo(5_000_000);
    }

    @Test
    void exemptItemsPayNoTax() {
        assertThat(FlipCalculator.tax(13190, 10_000_000)).isZero();
    }

    @Test
    void buysLowAndSellsHighAfterTax() {
        ItemDto item = item();
        item.highPriceVolume5m = 100L;
        item.lowPriceVolume5m = 100L;

        FlipDto flip = FlipCalculator.calculate(item, PriceBasis.LATEST, null);

        assertThat(flip.buyPrice).isEqualTo(900);
        assertThat(flip.sellPrice).isEqualTo(1_000);
        assertThat(flip.tax).isEqualTo(20);
        assertThat(flip.margin).isEqualTo(80);
        assertThat(flip.roi).isCloseTo(80.0 / 900, within(1e-9));
        assertThat(flip.quantity).isEqualTo(100);
        assertThat(flip.potentialProfit).isEqualTo(8_000);
        assertThat(flip.volume5m).isEqualTo(200);
        assertThat(flip.fillableQuantity).isEqualTo(100);
        assertThat(flip.limitedBy).isEqualTo(LimitedBy.BUY_LIMIT);
        assertThat(flip.estimatedProfit).isEqualTo(8_000);
        assertThat(flip.lastTradeTime).isEqualTo(NOW - 120);
    }

    @Test
    void slowerSideOfTheFiveMinuteVolumeCapsTheEstimate() {
        ItemDto item = item();
        item.buyLimit = 1_000;

        FlipDto flip = FlipCalculator.calculate(item, PriceBasis.LATEST, null);

        // Eight instant sells per five minutes fill 8 × 48 = 384 buys in four hours.
        assertThat(flip.potentialProfit).isEqualTo(80_000);
        assertThat(flip.fillableQuantity).isEqualTo(384);
        assertThat(flip.limitedBy).isEqualTo(LimitedBy.VOLUME);
        assertThat(flip.estimatedProfit).isEqualTo(80 * 384);
    }

    @Test
    void noRecentTradesMeansNothingFills() {
        ItemDto item = item();
        item.highPriceVolume5m = null;
        item.lowPriceVolume5m = null;

        FlipDto flip = FlipCalculator.calculate(item, PriceBasis.LATEST, null);

        assertThat(flip.volume5m).isNull();
        assertThat(flip.fillableQuantity).isZero();
        assertThat(flip.limitedBy).isEqualTo(LimitedBy.VOLUME);
        assertThat(flip.estimatedProfit).isZero();
        assertThat(flip.potentialProfit).isEqualTo(8_000);
    }

    @Test
    void cashStackLimitsTheQuantity() {
        ItemDto item = item();
        item.highPriceVolume5m = 100L;
        item.lowPriceVolume5m = 100L;

        FlipDto flip = FlipCalculator.calculate(item, PriceBasis.LATEST, 45_000L);

        assertThat(flip.quantity).isEqualTo(50);
        assertThat(flip.fillableQuantity).isEqualTo(50);
        assertThat(flip.limitedBy).isEqualTo(LimitedBy.CASH_STACK);
        assertThat(flip.estimatedProfit).isEqualTo(4_000);
    }

    @Test
    void reportsLossesWhenTaxEatsTheSpread() {
        ItemDto item = item();
        item.lowPrice = 990L;

        FlipDto flip = FlipCalculator.calculate(item, PriceBasis.LATEST, null);

        assertThat(flip.margin).isEqualTo(-10);
        assertThat(flip.potentialProfit).isEqualTo(-1_000);
        assertThat(flip.estimatedProfit).isEqualTo(-10 * 100);
    }

    @Test
    void pricesFromRoundedFiveMinuteAverages() {
        FlipDto flip = FlipCalculator.calculate(item(), PriceBasis.AVERAGE_5M, null);

        assertThat(flip.buyPrice).isEqualTo(911);
        assertThat(flip.sellPrice).isEqualTo(990);
        assertThat(flip.margin).isEqualTo(990 - 19 - 911);
    }

    @Test
    void leavesProfitEmptyWhenAPriceIsMissing() {
        ItemDto item = item();
        item.lowPrice = null;
        item.lowPriceTime = null;

        FlipDto flip = FlipCalculator.calculate(item, PriceBasis.LATEST, null);

        assertThat(flip.margin).isNull();
        assertThat(flip.roi).isNull();
        assertThat(flip.potentialProfit).isNull();
        assertThat(flip.estimatedProfit).isNull();
        assertThat(flip.lastTradeTime).isNull();
    }

    @Test
    void unknownBuyLimitLeavesOnlyTheVolumeCap() {
        ItemDto item = item();
        item.buyLimit = null;

        FlipDto flip = FlipCalculator.calculate(item, PriceBasis.LATEST, null);

        assertThat(flip.quantity).isNull();
        assertThat(flip.potentialProfit).isNull();
        assertThat(flip.fillableQuantity).isEqualTo(384);
        assertThat(flip.estimatedProfit).isEqualTo(80 * 384);
    }
}
