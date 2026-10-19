package com.stockmanagement.market;

import java.math.BigDecimal;
import java.time.Instant;

/** Supplies shared market data without knowing which user requested it. */
public interface QuoteProvider {
    String name();

    QuoteSnapshot fetch(SecurityCatalog.Security security, Instant collectedAt);

    record QuoteSnapshot(BigDecimal price, BigDecimal change, BigDecimal changeRate,
                         Instant sourceTimestamp, Instant retrievedAt) { }

    class Unavailable extends RuntimeException {
        public Unavailable(String message) { super(message); }
    }

    class Updating extends RuntimeException {
        public Updating(String message) { super(message); }
    }
}
