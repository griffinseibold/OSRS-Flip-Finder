package com.flipfinder.service;

import com.fasterxml.jackson.annotation.JsonValue;

/** Which flips to return and how to order them. */
public record FlipQuery(
        PriceBasis basis,
        String search,
        Membership membership,
        // Both sides must have traded within this many minutes; 0 means any time. Latest prices only.
        int maxTradeAgeMinutes,
        long minVolume5m,
        // Coins available to spend, or null for no limit.
        Long cashStack,
        Sort sort,
        Direction direction) {

    public FlipQuery {
        basis = basis == null ? PriceBasis.LATEST : basis;
        search = search == null ? "" : search.trim();
        membership = membership == null ? Membership.ALL : membership;
        cashStack = cashStack == null || cashStack <= 0 ? null : cashStack;
        sort = sort == null ? Sort.ESTIMATED_PROFIT : sort;
        if (direction == null) {
            direction = sort == Sort.NAME ? Direction.ASC : Direction.DESC;
        }
    }

    public enum PriceBasis {
        /** The most recent instant-buy and instant-sell prices. */
        LATEST("latest"),
        /** Average prices traded in the latest five-minute window. */
        AVERAGE_5M("average5m");

        private final String value;

        PriceBasis(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum Membership {
        ALL("all"),
        F2P("f2p"),
        MEMBERS("members");

        private final String value;

        Membership(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum Sort {
        ESTIMATED_PROFIT("estimatedProfit"),
        POTENTIAL_PROFIT("potentialProfit"),
        MARGIN("margin"),
        ROI("roi"),
        BUY_PRICE("buyPrice"),
        SELL_PRICE("sellPrice"),
        BUY_LIMIT("buyLimit"),
        VOLUME_5M("volume5m"),
        LAST_TRADE_TIME("lastTradeTime"),
        NAME("name");

        private final String value;

        Sort(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum Direction {
        ASC("asc"),
        DESC("desc");

        private final String value;

        Direction(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }
}
