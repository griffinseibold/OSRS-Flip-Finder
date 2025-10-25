package com.flipfinder.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.flipfinder.dto.ItemDto;

import java.util.List;

@Repository
public class ItemRepository {
    private final JdbcTemplate jdbc;

    public ItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countAll() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM records", Long.class);
    }

    public List<ItemDto> findPage(int limit, int offset) {
        // ORDER BY ensures deterministic paging
        final String sql = """
                SELECT
                CAST(id AS TEXT) AS id,
                name,
                source_updated_at,
                updated_at,
                data
                FROM records
                ORDER BY id
                LIMIT ? OFFSET ?
                """;

        return jdbc.query(sql, (rs, i) -> {
            ItemDto r = new ItemDto();
            r.id = rs.getString("id");
            r.name = rs.getString("name");
            r.sourceUpdatedAt = rs.getString("source_updated_at");
            r.updatedAt = rs.getString("updated_at");
            r.data = rs.getString("data");
            return r;
        }, limit, offset);
    }
}