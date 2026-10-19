package com.stockmanagement.watchlist;

import java.time.Instant;

public record WatchlistItem(Market market, String symbol, String name,
                            String currency, Instant createdAt) { }
