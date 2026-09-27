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

    @Schema(description = "One four-hour buy limit, reduced to what the cash stack affords")
    public Long quantity;

    @Schema(description = "Margin across the whole quantity")
    public Long potentialProfit;

    @Schema(description = "Items traded in the latest five-minute window, at both prices")
    public Long volume5m;

    @Schema(description = "Quantity that could fill in four hours at the five-minute trading pace")
    public Long fillableQuantity;

    @Schema(description = "Margin across the fillable quantity: the expected four-hour profit")
    public Long estimatedProfit;

    @Schema(description = "What caps the fillable quantity")
    public LimitedBy limitedBy;

    @Schema(description = "Unix seconds of the older of the latest instant-buy and instant-sell trades")
    public Long lastTradeTime;

    public enum LimitedBy {
        BUY_LIMIT("buyLimit"),
        CASH_STACK("cashStack"),
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
