package com.flipfinder.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionOperations;

/** Each item's average prices and volumes per five-minute or hourly bucket. */
@Repository
public class PriceHistoryRepository {
    public static final int FIVE_MINUTES = 300;
    public static final int HOUR = 3600;

    private final JdbcTemplate jdbc;
    private final TransactionOperations transactions;

    public PriceHistoryRepository(JdbcTemplate jdbc, TransactionOperations transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    /** One item's trading in one bucket. Prices are null for a side that did not trade. */
    public record Point(long timestamp, Long avgHighPrice, long highVolume, Long avgLowPrice, long lowVolume) {
        public long volume() {
            return highVolume + lowVolume;
        }
    }

    public record Row(int itemId, Long avgHighPrice, long highVolume, Long avgLowPrice, long lowVolume) {
    }

    /** Stores a bucket and records it as imported, in one transaction. */
    public void saveBucket(int step, long timestamp, List<Row> rows) {
        transactions.executeWithoutResult(status -> {
            jdbc.batchUpdate("""
                    INSERT OR REPLACE INTO price_history (
                      step, item_id, timestamp, avg_high_price, high_volume, avg_low_price, low_volume
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, rows.stream()
                    .map(row -> new Object[] {
                            step, row.itemId(), timestamp,
                            row.avgHighPrice(), row.highVolume(), row.avgLowPrice(), row.lowVolume() })
                    .toList());
            jdbc.update("INSERT OR REPLACE INTO price_history_buckets (step, timestamp, items) VALUES (?, ?, ?)",
                    step, timestamp, rows.size());
        });
    }

    /** Start times of the buckets already imported since {@code since}. */
    public Set<Long> importedBuckets(int step, long since) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT timestamp FROM price_history_buckets WHERE step = ? AND timestamp >= ?",
                Long.class, step, since));
    }

    /** Start times of imported buckets that had any trades, oldest first. */
    public List<Long> tradedBuckets(int step, long since) {
        return jdbc.queryForList("""
                SELECT timestamp FROM price_history_buckets
                WHERE step = ? AND timestamp >= ? AND items > 0
                ORDER BY timestamp
                """, Long.class, step, since);
    }

    /** One item's buckets since {@code since}, oldest first. Buckets it did not trade in are missing. */
    public List<Point> series(int step, int itemId, long since) {
        return jdbc.query("""
                SELECT timestamp, avg_high_price, high_volume, avg_low_price, low_volume
                FROM price_history
                WHERE step = ? AND item_id = ? AND timestamp >= ?
                ORDER BY timestamp
                """, PriceHistoryRepository::mapPoint, step, itemId, since);
    }

    /** Deletes buckets that started before {@code before}. */
    public int prune(int step, long before) {
        return transactions.execute(status -> {
            jdbc.update("DELETE FROM price_history_buckets WHERE step = ? AND timestamp < ?", step, before);
            return jdbc.update("DELETE FROM price_history WHERE step = ? AND timestamp < ?", step, before);
        });
    }

    private static Point mapPoint(ResultSet rs, int rowNum) throws SQLException {
        return new Point(
                rs.getLong("timestamp"),
                nullableLong(rs, "avg_high_price"),
                rs.getLong("high_volume"),
                nullableLong(rs, "avg_low_price"),
                rs.getLong("low_volume"));
    }

    // sqlite-jdbc cannot read a NULL through getObject(column, Long.class).
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
