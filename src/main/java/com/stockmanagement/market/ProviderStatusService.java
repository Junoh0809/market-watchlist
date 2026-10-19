package com.stockmanagement.market;

import com.stockmanagement.watchlist.Market;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class ProviderStatusService implements QuoteAttemptRecorder {
    private final JdbcTemplate jdbc;

    public ProviderStatusService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ProviderStatus> findAll() {
        return jdbc.query("""
                        SELECT provider, market, symbol, status, last_attempt_at, last_success_at,
                               last_failure_at, source_timestamp, last_error
                        FROM provider_status ORDER BY provider, market, symbol
                        """,
                (rs, row) -> new ProviderStatus(rs.getString("provider"),
                        Market.valueOf(rs.getString("market")), rs.getString("symbol"),
                        QuoteService.QuoteStatus.valueOf(rs.getString("status")),
                        instant(rs.getTimestamp("last_attempt_at")),
                        instant(rs.getTimestamp("last_success_at")),
                        instant(rs.getTimestamp("last_failure_at")),
                        instant(rs.getTimestamp("source_timestamp")), rs.getString("last_error")));
    }

    @Override
    public void success(String provider, SecurityCatalog.Security security, Instant attemptedAt,
                        Instant sourceTimestamp, QuoteService.QuoteStatus status) {
        ensureRow(provider, security, attemptedAt);
        jdbc.update("""
                        UPDATE provider_status SET status = ?, last_attempt_at = ?, last_success_at = ?,
                            source_timestamp = ?, last_error = NULL
                        WHERE provider = ? AND market = ? AND symbol = ?
                        """,
                status.name(), Timestamp.from(attemptedAt), Timestamp.from(attemptedAt),
                Timestamp.from(sourceTimestamp), provider, security.market().name(), security.symbol());
    }

    @Override
    public void failure(String provider, SecurityCatalog.Security security, Instant attemptedAt,
                        QuoteService.QuoteStatus status, String message) {
        ensureRow(provider, security, attemptedAt);
        jdbc.update("""
                        UPDATE provider_status SET status = ?, last_attempt_at = ?, last_failure_at = ?,
                            last_error = ? WHERE provider = ? AND market = ? AND symbol = ?
                        """,
                status.name(), Timestamp.from(attemptedAt), Timestamp.from(attemptedAt),
                message.length() > 256 ? message.substring(0, 256) : message,
                provider, security.market().name(), security.symbol());
    }

    @Override
    public void updating(String provider, SecurityCatalog.Security security, Instant attemptedAt) {
        ensureRow(provider, security, attemptedAt);
        jdbc.update("""
                        UPDATE provider_status SET status = ?, last_attempt_at = ?
                        WHERE provider = ? AND market = ? AND symbol = ?
                        """,
                QuoteService.QuoteStatus.UPDATING.name(), Timestamp.from(attemptedAt),
                provider, security.market().name(), security.symbol());
    }

    private void ensureRow(String provider, SecurityCatalog.Security security, Instant attemptedAt) {
        Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM provider_status WHERE provider = ? AND market = ? AND symbol = ?
                        """, Integer.class, provider, security.market().name(), security.symbol());
        if (count != null && count > 0) return;
        try {
            jdbc.update("""
                            INSERT INTO provider_status (provider, market, symbol, status, last_attempt_at)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    provider, security.market().name(), security.symbol(),
                    QuoteService.QuoteStatus.UPDATING.name(), Timestamp.from(attemptedAt));
        } catch (DuplicateKeyException ignored) {
            // Another request inserted the shared symbol first.
        }
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }

    public record ProviderStatus(String provider, Market market, String symbol,
                                 QuoteService.QuoteStatus status, Instant lastAttemptAt,
                                 Instant lastSuccessAt, Instant lastFailureAt,
                                 Instant sourceTimestamp, String lastError) { }
}
