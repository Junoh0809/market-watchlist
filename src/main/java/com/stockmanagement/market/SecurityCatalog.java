package com.stockmanagement.market;

import com.stockmanagement.watchlist.Market;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Component
public class SecurityCatalog {
    private static final List<Security> SECURITIES = List.of(
            new Security(Market.KRX, "005930", "삼성전자", "KRW"),
            new Security(Market.KRX, "000660", "SK하이닉스", "KRW"),
            new Security(Market.KRX, "035420", "NAVER", "KRW"),
            new Security(Market.US, "AAPL", "Apple Inc.", "USD"),
            new Security(Market.US, "MSFT", "Microsoft Corp.", "USD"),
            new Security(Market.US, "NVDA", "NVIDIA Corp.", "USD"),
            new Security(Market.US, "AMZN", "Amazon.com Inc.", "USD")
    );

    public Security require(Market market, String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return SECURITIES.stream()
                .filter(security -> security.market() == market && security.symbol().equals(normalized))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Security not found"));
    }

    public List<Security> search(Market market, String query, int limit) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return SECURITIES.stream()
                .filter(security -> security.market() == market)
                .filter(security -> normalized.isBlank()
                        || security.symbol().toLowerCase(Locale.ROOT).contains(normalized)
                        || security.name().toLowerCase(Locale.ROOT).contains(normalized))
                .limit(limit).toList();
    }

    public record Security(Market market, String symbol, String name, String currency) { }
}
