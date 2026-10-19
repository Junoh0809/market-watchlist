package com.stockmanagement.market;

import java.time.Instant;

public interface QuoteAttemptRecorder {
    void success(String provider, SecurityCatalog.Security security, Instant attemptedAt,
                 Instant sourceTimestamp, QuoteService.QuoteStatus status);

    void failure(String provider, SecurityCatalog.Security security, Instant attemptedAt,
                 QuoteService.QuoteStatus status, String message);

    void updating(String provider, SecurityCatalog.Security security, Instant attemptedAt);

    QuoteAttemptRecorder NOOP = new QuoteAttemptRecorder() {
        @Override public void success(String provider, SecurityCatalog.Security security, Instant attemptedAt,
                                      Instant sourceTimestamp, QuoteService.QuoteStatus status) { }
        @Override public void failure(String provider, SecurityCatalog.Security security, Instant attemptedAt,
                                      QuoteService.QuoteStatus status, String message) { }
        @Override public void updating(String provider, SecurityCatalog.Security security, Instant attemptedAt) { }
    };
}
