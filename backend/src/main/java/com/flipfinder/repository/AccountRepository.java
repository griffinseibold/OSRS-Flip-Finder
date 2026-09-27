package com.flipfinder.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The latest RuneLite snapshot of each account, stored as JSON. */
@Repository
public class AccountRepository {
    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(long accountHash, String displayName, String snapshotJson, String receivedAt) {
        jdbc.update("""
                INSERT INTO runelite_accounts (account_hash, display_name, snapshot, received_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(account_hash) DO UPDATE SET
                  display_name = excluded.display_name,
                  snapshot = excluded.snapshot,
                  received_at = excluded.received_at
                """, accountHash, displayName, snapshotJson, receivedAt);
    }

    public Optional<StoredAccount> find(long accountHash) {
        return jdbc.query(SELECT_ACCOUNTS + " WHERE account_hash = ?", AccountRepository::map, accountHash)
                .stream()
                .findFirst();
    }

    public List<StoredAccount> findAll() {
        return jdbc.query(SELECT_ACCOUNTS + " ORDER BY received_at DESC", AccountRepository::map);
    }

    private static final String SELECT_ACCOUNTS = "SELECT account_hash, snapshot, received_at FROM runelite_accounts";

    private static StoredAccount map(ResultSet rs, int rowNum) throws SQLException {
        return new StoredAccount(rs.getLong("account_hash"), rs.getString("snapshot"), rs.getString("received_at"));
    }

    public record StoredAccount(long accountHash, String snapshotJson, String receivedAt) {
    }
}
