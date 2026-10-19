package com.stockmanagement.market;

import com.stockmanagement.watchlist.Market;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class QuoteServiceTest {
    private static final SecurityCatalog.Security APPLE =
            new SecurityCatalog.Security(Market.US, "AAPL", "Apple Inc.", "USD");

    @Test
    void failedRefreshKeepsLastSuccessfulPriceAndBothTimestamps() {
        var clock = new MutableClock("2026-10-16T00:00:10Z");
        var provider = new MockQuoteProvider(MockQuoteProvider.Scenario.NORMAL);
        var quotes = new QuoteService(provider, clock);

        var fresh = quotes.quote(APPLE);
        assertEquals(QuoteService.QuoteStatus.NORMAL, fresh.status());
        provider.setScenario(MockQuoteProvider.Scenario.FAILURE);
        clock.moveTo("2026-10-16T00:01:10Z");

        var afterFailure = quotes.quote(APPLE);
        assertEquals(QuoteService.QuoteStatus.LAST_KNOWN, afterFailure.status());
        assertEquals(fresh.snapshot().price(), afterFailure.snapshot().price());
        assertEquals(fresh.snapshot().sourceTimestamp(), afterFailure.snapshot().sourceTimestamp());
        assertEquals(fresh.snapshot().retrievedAt(), afterFailure.snapshot().retrievedAt());

        provider.setScenario(MockQuoteProvider.Scenario.NORMAL);
        clock.moveTo("2026-10-16T00:02:10Z");
        var recovered = quotes.quote(APPLE);
        assertEquals(QuoteService.QuoteStatus.NORMAL, recovered.status());
        assertTrue(recovered.snapshot().sourceTimestamp().isAfter(fresh.snapshot().sourceTimestamp()));
    }

    @Test
    void delayedRefreshCannotReplaceANewerLastKnownQuote() {
        var clock = new MutableClock("2026-10-16T00:00:10Z");
        var provider = new MockQuoteProvider(MockQuoteProvider.Scenario.NORMAL);
        var quotes = new QuoteService(provider, clock);
        var fresh = quotes.quote(APPLE);

        provider.setScenario(MockQuoteProvider.Scenario.DELAYED);
        clock.moveTo("2026-10-16T00:01:10Z");
        var delayed = quotes.quote(APPLE);

        assertEquals(QuoteService.QuoteStatus.LAST_KNOWN, delayed.status());
        assertEquals(fresh.snapshot(), delayed.snapshot());
    }

    @Test
    void delayedFirstCollectionIsMarkedLastKnownAndFailureWithoutDataIsUpdating() {
        var clock = new MutableClock("2026-10-16T00:00:10Z");
        var delayed = new QuoteService(new MockQuoteProvider(MockQuoteProvider.Scenario.DELAYED), clock).quote(APPLE);
        assertEquals(QuoteService.QuoteStatus.LAST_KNOWN, delayed.status());
        assertTrue(delayed.snapshot().sourceTimestamp().isBefore(clock.instant()));

        var unavailable = new QuoteService(new MockQuoteProvider(MockQuoteProvider.Scenario.FAILURE), clock).quote(APPLE);
        assertEquals(QuoteService.QuoteStatus.UPDATING, unavailable.status());
        assertNull(unavailable.snapshot());
    }

    @Test
    void updatingRefreshKeepsLastSuccessfulPrice() {
        var clock = new MutableClock("2026-10-16T00:00:10Z");
        var provider = new MockQuoteProvider(MockQuoteProvider.Scenario.NORMAL);
        var quotes = new QuoteService(provider, clock);
        var fresh = quotes.quote(APPLE);

        provider.setScenario(MockQuoteProvider.Scenario.UPDATING);
        clock.moveTo("2026-10-16T00:01:10Z");
        var updating = quotes.quote(APPLE);

        assertEquals(QuoteService.QuoteStatus.UPDATING, updating.status());
        assertEquals(fresh.snapshot(), updating.snapshot());
    }

    @Test
    void usersShareOneCollectionPerSecurityPerMinute() {
        var clock = new MutableClock("2026-10-16T00:00:10Z");
        var counting = new CountingProvider();
        var quotes = new QuoteService(counting, clock);
        var first = quotes.quote(APPLE);
        var second = quotes.quote(APPLE);
        assertEquals(first.snapshot(), second.snapshot());
        assertEquals(1, counting.calls);

        clock.moveTo("2026-10-16T00:01:10Z");
        quotes.quote(APPLE);
        assertEquals(2, counting.calls);
    }

    private static class CountingProvider implements QuoteProvider {
        private final MockQuoteProvider delegate = new MockQuoteProvider(MockQuoteProvider.Scenario.NORMAL);
        int calls;

        @Override public String name() { return delegate.name(); }
        @Override public QuoteSnapshot fetch(SecurityCatalog.Security security, Instant collectedAt) {
            calls++;
            return delegate.fetch(security, collectedAt);
        }
    }

    private static class MutableClock extends Clock {
        private Instant current;

        MutableClock(String instant) { current = Instant.parse(instant); }
        void moveTo(String instant) { current = Instant.parse(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
