package com.flipfinder.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flipfinder.dto.ItemDto;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class ItemRepository {
    private final JdbcTemplate jdbc;

    public ItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countAll() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM items", Long.class);
    }

    public List<ItemDto> findPage(int limit, int offset) {
        return jdbc.query(SELECT_ITEMS + " ORDER BY id LIMIT ? OFFSET ?", ItemRepository::mapItem, limit, offset);
    }

    public List<ItemDto> findAll() {
        return jdbc.query(SELECT_ITEMS, ItemRepository::mapItem);
    }

    public Optional<ItemDto> findById(int id) {
        return jdbc.query(SELECT_ITEMS + " WHERE id = ?", ItemRepository::mapItem, id).stream().findFirst();
    }

    private static final String SELECT_ITEMS = """
            SELECT
              id,
              name,
              examine,
              members,
              low_alchemy,
              high_alchemy,
              buy_limit,
              value,
              icon,
              high_price,
              high_price_time,
              low_price,
              low_price_time,
              average_high_price_5m,
              high_price_volume_5m,
              average_low_price_5m,
              low_price_volume_5m,
              five_minute_timestamp,
              updated_at
            FROM items
            """;

    private static ItemDto mapItem(ResultSet rs, int rowNum) throws SQLException {
        ItemDto item = new ItemDto();
        item.id = rs.getInt("id");
        item.name = rs.getString("name");
        item.examine = rs.getString("examine");
        item.members = rs.getBoolean("members");
        item.lowAlchemy = nullableLong(rs, "low_alchemy");
        item.highAlchemy = nullableLong(rs, "high_alchemy");
        item.buyLimit = nullableInteger(rs, "buy_limit");
        item.value = nullableLong(rs, "value");
        item.icon = rs.getString("icon");
        item.highPrice = nullableLong(rs, "high_price");
        item.highPriceTime = nullableLong(rs, "high_price_time");
        item.lowPrice = nullableLong(rs, "low_price");
        item.lowPriceTime = nullableLong(rs, "low_price_time");
        item.averageHighPrice5m = nullableDouble(rs, "average_high_price_5m");
        item.highPriceVolume5m = nullableLong(rs, "high_price_volume_5m");
        item.averageLowPrice5m = nullableDouble(rs, "average_low_price_5m");
        item.lowPriceVolume5m = nullableLong(rs, "low_price_volume_5m");
        item.fiveMinuteTimestamp = nullableLong(rs, "five_minute_timestamp");
        item.updatedAt = rs.getString("updated_at");
        return item;
    }

    // sqlite-jdbc's getObject(column, Long.class) throws on NULL instead of
    // returning null, so read primitives and check wasNull().
    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
