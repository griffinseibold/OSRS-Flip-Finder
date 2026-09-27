package com.flipfinder.service;

import com.flipfinder.dto.ItemDto;
import com.flipfinder.dto.ItemHistoryDto;
import com.flipfinder.repository.PriceHistoryRepository.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compares an item's latest trading with its stored history. The numbers are
 * worked out here rather than by the language model, which is unreliable at
 * arithmetic over hundreds of values.
 */
final class TradingHistory {
    static final double UNUSUAL = 3;
    static final double ABOVE = 1.5;
    static final double BELOW = 0.5;
    // Six hours of five-minute buckets, and three days for a typical hour of the day.
    static final int MIN_FIVE_MINUTE_BUCKETS = 72;
    static final int MIN_DAYS_FOR_HOUR_OF_DAY = 3;
    private static final int BUCKETS_PER_HOUR = 12;
    private static final long DAY = 86_400;

    private TradingHistory() {
    }

    /**
     * @param fiveMinute the item's five-minute buckets, oldest first
     * @param fiveMinuteBuckets every stored five-minute bucket with any trades, oldest first;
     *        the item did not trade in those missing from {@code fiveMinute}
     */
    static ItemHistoryDto summarize(ItemDto item, List<Point> fiveMinute, List<Long> fiveMinuteBuckets,
            List<Point> hourly, List<Long> hourlyBuckets) {
        ItemHistoryDto history = new ItemHistoryDto();
        history.itemId = item.id;
        history.name = item.name;
        history.icon = item.icon;
        history.fiveMinuteHours = round1(fiveMinuteBuckets.size() / (double) BUCKETS_PER_HOUR);
        history.hourlyDays = round1(hourlyBuckets.size() / 24.0);
        history.volume = volume(fiveMinute, fiveMinuteBuckets, hourly, hourlyBuckets);
        long reference = history.volume.latestBucket != null
                ? history.volume.latestBucket
                : hourlyBuckets.isEmpty() ? 0 : hourlyBuckets.getLast();
        history.price = price(fiveMinute, hourly, reference);
        history.margin = margin(item, fiveMinute);
        history.notes = notes(history);
        return history;
    }

    private static ItemHistoryDto.Volume volume(List<Point> fiveMinute, List<Long> fiveMinuteBuckets,
            List<Point> hourly, List<Long> hourlyBuckets) {
        ItemHistoryDto.Volume volume = new ItemHistoryDto.Volume();
        if (fiveMinuteBuckets.isEmpty()) {
            return volume;
        }
        long[] volumes = volumes(fiveMinute, fiveMinuteBuckets);
        long latest = fiveMinuteBuckets.getLast();
        volume.latestBucket = latest;
        volume.last5m = volumes[volumes.length - 1];

        if (volumes.length >= MIN_FIVE_MINUTE_BUCKETS) {
            volume.typical5m = typical(volumes);
            long below = Arrays.stream(volumes).filter(v -> v < volume.last5m).count();
            volume.percentile = (int) Math.round(100.0 * below / volumes.length);
        }

        // Trading follows the time of day, so compare with the same hour on earlier days.
        long hourOfDay = Math.floorMod(latest, DAY) / 3600;
        List<Long> sameHour = new ArrayList<>();
        for (long bucket : hourlyBuckets) {
            if (Math.floorMod(bucket, DAY) / 3600 == hourOfDay && bucket < latest - 3600) {
                sameHour.add(bucket);
            }
        }
        if (sameHour.size() >= MIN_DAYS_FOR_HOUR_OF_DAY) {
            volume.typical5mThisHour = round1(typical(volumes(hourly, sameHour)) / BUCKETS_PER_HOUR);
        }

        Double baseline = volume.typical5mThisHour != null ? volume.typical5mThisHour : volume.typical5m;
        if (baseline == null) {
            return volume;
        }
        // Judge the exact ratio; the rounded one is for display.
        Double ratio = baseline > 0 ? volume.last5m / baseline : null;
        volume.ratio = ratio == null ? null : round1(ratio);
        volume.verdict = verdict(ratio, volume.last5m);

        // Five-minute buckets in the last hour, to tell a burst from a lasting surge.
        long lastHour = 0;
        int buckets = 0;
        for (int i = volumes.length - 1; i >= 0 && fiveMinuteBuckets.get(i) > latest - 3600; i--) {
            lastHour += volumes[i];
            buckets++;
        }
        if (buckets >= BUCKETS_PER_HOUR / 2) {
            volume.lastHour = Math.round(lastHour * (double) BUCKETS_PER_HOUR / buckets);
            volume.typicalHour = round1(baseline * BUCKETS_PER_HOUR);
            if (baseline > 0) {
                volume.lastHourRatio = round1(volume.lastHour / volume.typicalHour);
            }
        }
        return volume;
    }

    private static String verdict(Double ratio, long last5m) {
        if (ratio == null) {
            // Nothing traded in the whole history, so any trading at all is unusual.
            return last5m > 0 ? "unusually high" : "typical";
        }
        if (ratio >= UNUSUAL) {
            return "unusually high";
        }
        if (ratio >= ABOVE) {
            return "above typical";
        }
        return ratio <= BELOW ? "below typical" : "typical";
    }

    private static ItemHistoryDto.Price price(List<Point> fiveMinute, List<Point> hourly, long reference) {
        ItemHistoryDto.Price price = new ItemHistoryDto.Price();
        List<Double> lastHour = new ArrayList<>();
        for (Point point : fiveMinute) {
            Double mid = mid(point);
            if (mid != null && point.timestamp() > reference - 3600) {
                lastHour.add(mid);
            }
        }
        if (!lastHour.isEmpty()) {
            price.current = round1(lastHour.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
        } else if (!hourly.isEmpty()) {
            price.current = mid(hourly.getLast());
        }
        if (price.current != null) {
            price.change24hPercent = change(price.current, priceAround(hourly, reference - DAY));
            price.change7dPercent = change(price.current, priceAround(hourly, reference - 7 * DAY));
            price.change30dPercent = change(price.current, priceAround(hourly, reference - 30 * DAY));
        }
        List<Point> range = hourly.isEmpty() ? fiveMinute : hourly;
        price.low = range.stream().map(Point::avgLowPrice).filter(p -> p != null).min(Long::compare).orElse(null);
        price.high = range.stream().map(Point::avgHighPrice).filter(p -> p != null).max(Long::compare).orElse(null);
        return price;
    }

    /** The median hourly price within three hours of {@code time}. */
    private static Double priceAround(List<Point> hourly, long time) {
        double[] mids = hourly.stream()
                .filter(point -> Math.abs(point.timestamp() - time) <= 3 * 3600)
                .map(TradingHistory::mid)
                .filter(mid -> mid != null)
                .mapToDouble(Double::doubleValue)
                .toArray();
        return mids.length == 0 ? null : median(mids);
    }

    private static Double change(double current, Double then) {
        return then == null || then == 0 ? null : round1((current - then) / then * 100);
    }

    private static ItemHistoryDto.Margin margin(ItemDto item, List<Point> fiveMinute) {
        ItemHistoryDto.Margin margin = new ItemHistoryDto.Margin();
        if (item.highPrice != null && item.lowPrice != null) {
            margin.current = item.highPrice - item.lowPrice;
        }
        double[] spreads = fiveMinute.stream()
                .filter(point -> point.avgHighPrice() != null && point.avgLowPrice() != null)
                .mapToDouble(point -> point.avgHighPrice() - point.avgLowPrice())
                .toArray();
        if (spreads.length < BUCKETS_PER_HOUR) {
            return margin;
        }
        margin.typical = round1(median(spreads));
        if (margin.current == null) {
            return margin;
        }
        double ratio = margin.typical > 0 ? margin.current / margin.typical
                : margin.current > 0 ? Double.POSITIVE_INFINITY : 1;
        if (margin.typical > 0) {
            margin.ratio = round1(ratio);
        }
        margin.verdict = ratio >= 2 ? "much wider than usual"
                : ratio >= 1.3 ? "wider than usual"
                : ratio <= 0.5 ? "narrower than usual"
                : "typical";
        return margin;
    }

    private static List<String> notes(ItemHistoryDto history) {
        List<String> notes = new ArrayList<>();
        ItemHistoryDto.Volume volume = history.volume;
        if (volume.verdict == null) {
            notes.add(String.format(Locale.ROOT, "There is not enough history yet to judge its volume: %s hours "
                    + "of five-minute data and %s days of hourly data.", history.fiveMinuteHours, history.hourlyDays));
        } else {
            String basis = volume.typical5mThisHour != null ? "for this time of day" : "over the last "
                    + span(history.fiveMinuteHours);
            Double typical = volume.typical5mThisHour != null ? volume.typical5mThisHour : volume.typical5m;
            StringBuilder note = new StringBuilder(String.format(Locale.ROOT,
                    "%,d traded in the latest five minutes, against a typical %,.0f %s", volume.last5m, typical, basis));
            if (volume.ratio != null) {
                note.append(String.format(Locale.ROOT, " (%s times typical)", volume.ratio));
            }
            note.append(": ").append(volume.verdict);
            if (volume.percentile != null) {
                note.append(String.format(Locale.ROOT, ", more than in %d%% of five-minute periods over the last %s",
                        volume.percentile, span(history.fiveMinuteHours)));
            }
            notes.add(note.append('.').toString());

            boolean high = "unusually high".equals(volume.verdict) || "above typical".equals(volume.verdict);
            if (high && volume.lastHourRatio != null) {
                notes.add(volume.lastHourRatio >= ABOVE
                        ? String.format(Locale.ROOT, "The last hour traded %s times its typical volume, so the "
                                + "surge has lasted.", volume.lastHourRatio)
                        : String.format(Locale.ROOT, "The last hour traded %s times its typical volume, so this is "
                                + "a short burst.", volume.lastHourRatio));
            }
        }

        ItemHistoryDto.Price price = history.price;
        if (price.current != null) {
            List<String> changes = new ArrayList<>();
            addChange(changes, price.change24hPercent, "a day ago");
            addChange(changes, price.change7dPercent, "a week ago");
            addChange(changes, price.change30dPercent, "30 days ago");
            StringBuilder note = new StringBuilder(String.format(Locale.ROOT,
                    "Its average price is %,.0f gp", price.current));
            if (!changes.isEmpty()) {
                note.append(", ").append(String.join(", ", changes));
            }
            note.append('.');
            if (price.low != null && price.high != null) {
                note.append(String.format(Locale.ROOT, " Over the last %s it traded between %,d and %,d gp.",
                        span(history.hourlyDays > 0 ? history.hourlyDays * 24 : history.fiveMinuteHours),
                        price.low, price.high));
            }
            notes.add(note.toString());
        }

        ItemHistoryDto.Margin margin = history.margin;
        if (margin.verdict != null) {
            String note = String.format(Locale.ROOT, "Its margin before tax is %,d gp, against a typical %,.0f gp: %s.",
                    margin.current, margin.typical, margin.verdict);
            if (margin.verdict.contains("wider")) {
                note += " A margin wider than usual often closes before offers fill.";
            }
            notes.add(note);
        }
        return notes;
    }

    /**
     * A short warning when a flip's volume or margin is far from normal, which
     * often means a spike that ends before offers fill; null when it is not.
     */
    static String warning(ItemHistoryDto history) {
        List<String> unusual = new ArrayList<>();
        if ("unusually high".equals(history.volume.verdict)) {
            unusual.add(history.volume.ratio == null
                    ? "trading after a long quiet spell"
                    : String.format(Locale.ROOT, "volume %sx usual", history.volume.ratio));
        }
        if ("much wider than usual".equals(history.margin.verdict)) {
            unusual.add(history.margin.ratio == null
                    ? "margin usually near zero"
                    : String.format(Locale.ROOT, "margin %sx usual", history.margin.ratio));
        }
        return unusual.isEmpty() ? null : String.join(", ", unusual) + ", may not last";
    }

    private static void addChange(List<String> changes, Double percent, String when) {
        if (percent == null) {
            return;
        }
        changes.add(Math.abs(percent) < 0.5
                ? "about the same as " + when
                : String.format(Locale.ROOT, "%s%% %s than %s", Math.abs(percent), percent > 0 ? "higher" : "lower",
                        when));
    }

    private static String span(double hours) {
        if (hours < 48) {
            return String.format(Locale.ROOT, "%.0f hours", Math.max(hours, 1));
        }
        return String.format(Locale.ROOT, "%.0f days", hours / 24);
    }

    /** Volumes for every bucket, counting a bucket the item is missing from as zero. */
    private static long[] volumes(List<Point> points, List<Long> buckets) {
        Map<Long, Point> byTime = new HashMap<>();
        for (Point point : points) {
            byTime.put(point.timestamp(), point);
        }
        return buckets.stream()
                .mapToLong(bucket -> {
                    Point point = byTime.get(bucket);
                    return point == null ? 0 : point.volume();
                })
                .toArray();
    }

    /**
     * The median, or the mean when the median is zero: an item that trades in
     * fewer than half the buckets still has a typical volume above zero.
     */
    private static double typical(long[] volumes) {
        double[] values = Arrays.stream(volumes).asDoubleStream().toArray();
        double median = median(values);
        return median > 0 ? median : Arrays.stream(values).average().orElse(0);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }

    /** The midpoint of the average buy and sell prices, or whichever side traded. */
    private static Double mid(Point point) {
        Long high = point.avgHighPrice();
        Long low = point.avgLowPrice();
        if (high != null && low != null) {
            return (high + low) / 2.0;
        }
        return high != null ? (double) high : low != null ? (double) low : null;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
