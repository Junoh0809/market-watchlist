package com.stockmanagement.market;

import com.stockmanagement.watchlist.Market;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class ExchangeTime {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private ExchangeTime() { }

    public static ZoneId zoneFor(Market market) {
        return switch (market) {
            case KRX -> SEOUL;
            case US -> NEW_YORK;
        };
    }

    public static ZonedDateTime atExchange(Market market, Instant instant) {
        return instant.atZone(zoneFor(market));
    }
}
