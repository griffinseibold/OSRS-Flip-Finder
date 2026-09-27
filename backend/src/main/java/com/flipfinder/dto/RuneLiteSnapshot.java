package com.flipfinder.dto;

import java.util.List;

/** What the RuneLite plugin reports about the logged-in account. */
public class RuneLiteSnapshot {
    public String displayName;
    public boolean members;
    public int membershipDays;
    public boolean ironman;
    public long inventoryCoins;
    public Long bankCoins; // null until the bank has been opened with the plugin running
    public Long bankCoinsSeenAt; // Unix seconds
    public List<Offer> geOffers = List.of();
    public List<BuyLimitWindow> buyLimits = List.of();
    public long capturedAt; // Unix seconds

    public static class Offer {
        public int slot;
        public int itemId;
        public String state; // net.runelite.api.GrandExchangeOfferState, such as BUYING or SOLD
        public int price;
        public int totalQuantity;
        public int quantityTraded;
        public int spent;
    }

    /** Items bought in a four-hour buy limit window, which starts at the first purchase. */
    public static class BuyLimitWindow {
        public int itemId;
        public long startedAt; // Unix seconds
        public int bought;
    }
}
