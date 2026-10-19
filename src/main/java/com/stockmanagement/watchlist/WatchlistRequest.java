package com.stockmanagement.watchlist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WatchlistRequest(@NotNull Market market, @NotBlank String symbol) { }
