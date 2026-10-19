package com.stockmanagement.market;

import com.stockmanagement.watchlist.Market;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExchangeTimeTest {
    @Test
    void newYorkExchangeTimeUsesDaylightSavingWhileSeoulDoesNot() {
        var winter = Instant.parse("2026-01-15T15:00:00Z");
        var summer = Instant.parse("2026-07-15T15:00:00Z");

        assertEquals(ZoneOffset.ofHours(-5), ExchangeTime.atExchange(Market.US, winter).getOffset());
        assertEquals(ZoneOffset.ofHours(-4), ExchangeTime.atExchange(Market.US, summer).getOffset());
        assertEquals(ZoneOffset.ofHours(9), ExchangeTime.atExchange(Market.KRX, winter).getOffset());
        assertEquals(ZoneOffset.ofHours(9), ExchangeTime.atExchange(Market.KRX, summer).getOffset());
    }
}
