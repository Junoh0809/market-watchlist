package com.stockmanagement.watchlist;

import com.stockmanagement.market.SecurityCatalog;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class WatchlistService {
    private final JdbcTemplate jdbc;
    private final SecurityCatalog catalog;

    public WatchlistService(JdbcTemplate jdbc, SecurityCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    public List<WatchlistItem> findAll(String userId) {
        return jdbc.query("""
                        SELECT market, symbol, name, currency, created_at
                        FROM watchlist_items WHERE account_id = ? ORDER BY created_at, market, symbol
                        """,
                (rs, row) -> new WatchlistItem(Market.valueOf(rs.getString("market")), rs.getString("symbol"),
                        rs.getString("name"), rs.getString("currency"), rs.getTimestamp("created_at").toInstant()),
                userId);
    }

    public WatchlistItem add(String userId, WatchlistRequest request) {
        var security = catalog.require(request.market(), request.symbol());
        var item = new WatchlistItem(security.market(), security.symbol(), security.name(),
                security.currency(), Instant.now());
        try {
            jdbc.update("""
                            INSERT INTO watchlist_items (account_id, market, symbol, name, currency, created_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    userId, item.market().name(), item.symbol(), item.name(), item.currency(),
                    Timestamp.from(item.createdAt()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Watchlist item already exists", exception);
        }
        return item;
    }

    public void delete(String userId, Market market, String symbol) {
        int removed = jdbc.update("DELETE FROM watchlist_items WHERE account_id = ? AND market = ? AND symbol = ?",
                userId, market.name(), symbol.trim().toUpperCase(Locale.ROOT));
        if (removed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Watchlist item not found");
    }
}
