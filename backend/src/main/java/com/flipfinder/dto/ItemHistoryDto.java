package com.flipfinder.dto;

import java.util.List;

/**
 * How an item's recent trading compares with its history. Volumes count both
 * sides: items bought at the high price plus items sold at the low price.
 */
public class ItemHistoryDto {
    public int itemId;
    public String name;
    public String icon;

    /** How much history the comparison is based on. */
    public double fiveMinuteHours;
    public double hourlyDays;

    public Volume volume;
    public Price price;
    public Margin margin;

    /** The comparison in plain sentences, for the language model to pass on. */
    public List<String> notes;

    public static class Volume {
        /** Start of the latest five-minute bucket, in epoch seconds. */
        public Long latestBucket;
        public Long last5m;
        /**
         * The median five-minute volume over the stored history; from hourly
         * history while there are under six hours of five-minute buckets.
         */
        public Double typical5m;
        /**
         * The median five-minute volume at this time of day, from hourly history: the
         * same hour and the hours either side of it on earlier days.
         */
        public Double typical5mThisHour;
        /** last5m divided by the typical volume, preferring the one for this hour. */
        public Double ratio;
        /** What the typical volume is based on, such as "for this time of day" or "over the last 2 days". */
        public String basis;
        /** Percentage of stored five-minute buckets that traded less than the latest one. */
        public Integer percentile;
        public Long lastHour;
        public Double typicalHour;
        public Double lastHourRatio;
        /** unusually high, above typical, typical, below typical, or null without enough history. */
        public String verdict;
    }

    public static class Price {
        /** The average traded price over the last hour: the midpoint of the buy and sell prices. */
        public Double current;
        public Double change24hPercent;
        public Double change7dPercent;
        public Double change30dPercent;
        public Long low;
        public Long high;
    }

    public static class Margin {
        /** The latest instant-buy price minus the latest instant-sell price, before tax. */
        public Long current;
        /** The median gap between the average buy and sell prices of five-minute buckets, or hourly ones. */
        public Double typical;
        public Double ratio;
        /**
         * much wider than usual (3 times or more), wider than usual (1.5 times), typical,
         * narrower than usual (half or less), or null without enough history.
         */
        public String verdict;
    }
}
