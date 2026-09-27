package com.flipfinder.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flipfinder.dto.ItemDto;
import com.flipfinder.dto.ItemHistoryDto;
import com.flipfinder.repository.PriceHistoryRepository.Point;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TradingHistoryTests {
    // Midnight UTC, so hours of the day are easy to place.
    private static final long DAY_START = 1_789_948_800L;
    private static final long DAY = 86_400;

    private static ItemDto item() {
        ItemDto item = new ItemDto();
        item.id = 1;
        item.name = "Gold leaf";
        item.highPrice = 1_020L;
        item.lowPrice = 1_000L;
        return item;
    }

    private static Point point(long timestamp, long volume) {
        return new Point(timestamp, 1_010L, volume / 2, 990L, volume - volume / 2);
    }

    /** Seven days of hourly trading: 1,200 an hour, but 2,400 at 20:00. */
    private static List<Point> hourly() {
        List<Point> hourly = new ArrayList<>();
        for (long bucket = DAY_START; bucket < DAY_START + 7 * DAY; bucket += 3600) {
            hourly.add(point(bucket, (bucket % DAY) / 3600 == 20 ? 2_400 : 1_200));
        }
        return hourly;
    }

    private static List<Long> timestamps(List<Point> points) {
        return points.stream().map(Point::timestamp).toList();
    }

    /** A day of five-minute buckets trading 100 each, ending at 20:55 on the eighth day. */
    private static List<Point> fiveMinute(long lastVolume) {
        long latest = DAY_START + 7 * DAY + 20 * 3600 + 55 * 60;
        List<Point> points = new ArrayList<>();
        for (long bucket = latest - DAY + 300; bucket <= latest; bucket += 300) {
            points.add(point(bucket, bucket == latest ? lastVolume : 100));
        }
        return points;
    }

    @Test
    void comparesTheLatestVolumeWithTheSameHourOnEarlierDays() {
        List<Point> fiveMinute = fiveMinute(1_200);
        List<Point> hourly = hourly();

        ItemHistoryDto history = TradingHistory.summarize(item(), fiveMinute, timestamps(fiveMinute),
                hourly, timestamps(hourly));

        // 20:00 usually trades 2,400 an hour: 200 per five minutes.
        assertThat(history.volume.typical5mThisHour).isEqualTo(200.0);
        assertThat(history.volume.typical5m).isEqualTo(100.0);
        assertThat(history.volume.last5m).isEqualTo(1_200);
        assertThat(history.volume.ratio).isEqualTo(6.0);
        assertThat(history.volume.verdict).isEqualTo("unusually high");
        assertThat(history.volume.percentile).isEqualTo(100);
        assertThat(history.fiveMinuteHours).isEqualTo(24.0);
        assertThat(history.hourlyDays).isEqualTo(7.0);

        // The rest of the hour was quiet, so this is a burst rather than a surge.
        assertThat(history.volume.lastHour).isEqualTo(2_300);
        assertThat(history.volume.lastHourRatio).isEqualTo(1.0);
        assertThat(history.notes).anySatisfy(note -> assertThat(note)
                .contains("1,200 traded in the latest five minutes", "6.0 times typical", "unusually high"));
        assertThat(history.notes).anySatisfy(note -> assertThat(note).contains("short burst"));
        assertThat(TradingHistory.warning(history)).isEqualTo("volume 6.0x usual, may not last");
    }

    @Test
    void countsBucketsAnItemDidNotTradeInAsZero() {
        List<Long> buckets = new ArrayList<>();
        List<Point> traded = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long bucket = DAY_START + i * 300L;
            buckets.add(bucket);
            if (i % 10 == 9) {
                traded.add(point(bucket, 10));
            }
        }

        ItemHistoryDto history = TradingHistory.summarize(item(), traded, buckets, List.of(), List.of());

        // Trading in one bucket in ten makes the median zero, so the mean of 1 stands in.
        assertThat(history.volume.typical5m).isEqualTo(1.0);
        assertThat(history.volume.last5m).isEqualTo(10);
        assertThat(history.volume.ratio).isEqualTo(10.0);
        assertThat(history.volume.verdict).isEqualTo("unusually high");
    }

    @Test
    void saysWhenThereIsNotEnoughHistory() {
        List<Point> points = fiveMinute(100).subList(0, 10);

        ItemHistoryDto history = TradingHistory.summarize(item(), points, timestamps(points), List.of(), List.of());

        assertThat(history.volume.last5m).isEqualTo(100);
        assertThat(history.volume.verdict).isNull();
        assertThat(history.notes.getFirst()).startsWith("There is not enough history yet");
    }

    @Test
    void comparesPriceAndMarginWithHistory() {
        // The last hour averaged 1,100 against 1,000 before, and the margin is five times its usual 20.
        List<Point> fiveMinute = new ArrayList<>();
        for (Point point : fiveMinute(100)) {
            boolean lastHour = point.timestamp() > fiveMinute(100).getLast().timestamp() - 3600;
            fiveMinute.add(lastHour ? new Point(point.timestamp(), 1_110L, 50, 1_090L, 50) : point);
        }
        ItemDto item = item();
        item.highPrice = 1_150L;
        item.lowPrice = 1_050L;
        List<Point> hourly = hourly();

        ItemHistoryDto history = TradingHistory.summarize(item, fiveMinute, timestamps(fiveMinute),
                hourly, timestamps(hourly));

        assertThat(history.price.current).isEqualTo(1_100.0);
        assertThat(history.price.change24hPercent).isEqualTo(10.0);
        assertThat(history.price.change7dPercent).isEqualTo(10.0);
        assertThat(history.price.change30dPercent).isNull();
        assertThat(history.price.low).isEqualTo(990);
        assertThat(history.price.high).isEqualTo(1_010);
        assertThat(history.margin.current).isEqualTo(100);
        assertThat(history.margin.typical).isEqualTo(20.0);
        assertThat(history.margin.verdict).isEqualTo("much wider than usual");
        assertThat(history.notes).anySatisfy(note -> assertThat(note)
                .contains("1,100 gp, 10.0% higher than a day ago, 10.0% higher than a week ago."));
        assertThat(history.notes).anySatisfy(note -> assertThat(note).contains("often closes before offers fill"));
        assertThat(TradingHistory.warning(history)).isEqualTo("margin 5.0x usual, may not last");
    }
}
