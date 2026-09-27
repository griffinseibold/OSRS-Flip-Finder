package com.flipfinder.dto;

import com.fasterxml.jackson.annotation.JsonValue;

import io.swagger.v3.oas.annotations.media.Schema;

/** An item priced as a flip: buy at the low price, sell at the high price, pay tax. */
public class FlipDto {
    public ItemDto item;

    @Schema(description = "Price to place a buy offer at: the instant-sell (low) price")
    public Long buyPrice;

    @Schema(description = "Price to place a sell offer at: the instant-buy (high) price")
    public Long sellPrice;

    @Schema(description = "Grand Exchange tax on one sale at the sell price")
    public Long tax;

    @Schema(description = "Coins made per item after tax; negative when the flip loses money")
    public Long margin;

    @Schema(description = "Margin as a fraction of the buy price")
    public Double roi;

    @Schema(description = "One four-hour buy limit, less what the account already bought, reduced to what "
            + "the budget affords; null when the item has no known buy limit")
    public Long quantity;

    @Schema(description = "Margin across the whole quantity")
    public Long potentialProfit;

    @Schema(description = "Items traded in the latest five-minute window, at both prices")
    public Long volume5m;

    @Schema(description = "Quantity that could fill in four hours at the five-minute trading pace; "
            + "null when the item has no known buy limit")
    public Long fillableQuantity;

    @Schema(description = "Margin across the fillable quantity: the expected four-hour profit")
    public Long estimatedProfit;

    @Schema(description = "What caps the fillable quantity; null when the item has no known buy limit")
    public LimitedBy limitedBy;

    @Schema(description = "Unix seconds of the older of the latest instant-buy and instant-sell trades")
    public Long lastTradeTime;

    @Schema(description = "Items the account already bought in its current buy limit window; null without one")
    public Long alreadyBought;

    @Schema(description = "Unix seconds when the account's buy limit window for this item resets; null without one")
    public Long limitResetsAt;

    @Schema(description = "Latest five-minute volume divided by the item's usual volume, from stored trading "
            + "history; null without enough history")
    public Double volumeVsUsual;

    @Schema(description = "Why the flip may not last, such as \"volume 18.2x usual, may not last\", when its "
            + "volume or margin is far from usual; null otherwise")
    public String warning;

    public enum LimitedBy {
        BUY_LIMIT("buyLimit"),
        BUDGET("budget"),
        VOLUME("volume");

        private final String value;

        LimitedBy(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }
}
