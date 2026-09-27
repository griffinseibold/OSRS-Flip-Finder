package com.flipfinder.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** An account as last reported by the RuneLite plugin, with item details filled in. */
public class AccountDto {
    public long accountHash;
    public String displayName;
    public boolean members;
    public int membershipDays;

    @Schema(description = "Ironman accounts cannot use the Grand Exchange")
    public boolean ironman;

    public long inventoryCoins;

    @Schema(description = "Coins in the bank when it was last opened; null until it has been seen")
    public Long bankCoins;

    public Long bankCoinsSeenAt;

    @Schema(description = "Inventory and bank coins together; null until the bank has been seen")
    public Long coins;

    public List<Offer> geOffers;

    @Schema(description = "Buy limit windows still running, most recently started first")
    public List<BuyLimitUse> buyLimits;

    @Schema(description = "Unix seconds when the plugin read this data")
    public long capturedAt;

    public String receivedAt;

    public static class Offer {
        public int slot;
        public int itemId;
        public String name;
        public String state;
        public int price;
        public int totalQuantity;
        public int quantityTraded;
        public int spent;
    }

    public static class BuyLimitUse {
        public int itemId;
        public String name;

        @Schema(description = "The item's four-hour buy limit; null when it is not documented")
        public Integer limit;

        public int bought;

        @Schema(description = "How many more can be bought before the window resets; null when the limit is unknown")
        public Integer remaining;

        @Schema(description = "Unix seconds when the window resets and the full limit is available again")
        public long resetsAt;
    }
}
