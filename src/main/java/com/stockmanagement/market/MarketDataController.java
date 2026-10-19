package com.stockmanagement.market;

import com.stockmanagement.auth.AuthService;
import com.stockmanagement.watchlist.Market;
import com.stockmanagement.watchlist.WatchlistItem;
import com.stockmanagement.watchlist.WatchlistService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MarketDataController {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant FIXED_MOCK_DATA_TIME = Instant.now().truncatedTo(ChronoUnit.MINUTES);
    private final AuthService auth;
    private final WatchlistService watchlist;
    private final QuoteService quotes;

    public MarketDataController(AuthService auth, WatchlistService watchlist, QuoteService quotes) {
        this.auth = auth;
        this.watchlist = watchlist;
        this.quotes = quotes;
    }

    @GetMapping("/me/quotes")
    public QuotesResponse quotes(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Instant now = Instant.now();
        var items = watchlist.findAll(auth.userId(authorization)).stream().map(this::quote).toList();
        return new QuotesResponse(items, ZonedDateTime.ofInstant(now, SEOUL), SEOUL.getId());
    }

    @GetMapping("/market-indices")
    public IndicesResponse indices(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.userId(authorization);
        Instant now = Instant.now();
        String status = now.isAfter(FIXED_MOCK_DATA_TIME.plus(1, ChronoUnit.MINUTES))
                ? "LAST_KNOWN" : "NORMAL";
        return new IndicesResponse(List.of(
                index("KOSPI", "KOSPI", "2680.15", "-12.33", status),
                index("KOSDAQ", "KOSDAQ", "781.20", "4.10", status),
                index("SPX", "S&P 500", "5820.25", "8.45", status),
                index("IXIC", "NASDAQ Composite", "18342.10", "55.20", status)
        ), ZonedDateTime.ofInstant(now, SEOUL));
    }

    @GetMapping("/me/news")
    public NewsResponse news(@RequestHeader(value = "Authorization", required = false) String authorization,
                             @RequestParam(name = "limit", defaultValue = "20") int limit) {
        Instant now = Instant.now();
        int safeLimit = Math.max(1, Math.min(limit, 50));
        var items = watchlist.findAll(auth.userId(authorization)).stream()
                .map(item -> new NewsItem("news-" + item.market() + item.symbol(), item.market(), item.symbol(),
                        item.name() + " market update", "Mock News", FIXED_MOCK_DATA_TIME,
                        ZonedDateTime.ofInstant(FIXED_MOCK_DATA_TIME, SEOUL), SEOUL.getId(), "https://example.com", "mock"))
                .limit(safeLimit).toList();
        return new NewsResponse(items, ZonedDateTime.ofInstant(now, SEOUL));
    }

    private QuoteItem quote(WatchlistItem item) {
        var security = new SecurityCatalog.Security(item.market(), item.symbol(), item.name(), item.currency());
        var result = quotes.quote(security);
        var snapshot = result.snapshot();
        return new QuoteItem(security,
                snapshot == null ? null : snapshot.price(),
                snapshot == null ? null : snapshot.change(),
                snapshot == null ? null : snapshot.changeRate(),
                result.source(),
                snapshot == null ? null : snapshot.sourceTimestamp(),
                snapshot == null ? null : ZonedDateTime.ofInstant(snapshot.sourceTimestamp(), SEOUL),
                SEOUL.getId(),
                snapshot == null ? null : ExchangeTime.atExchange(item.market(), snapshot.sourceTimestamp()),
                ExchangeTime.zoneFor(item.market()).getId(),
                snapshot == null ? null : snapshot.retrievedAt(),
                result.status().name());
    }

    private static IndexItem index(String code, String name, String base, String change, String status) {
        var value = new BigDecimal(base);
        var delta = new BigDecimal(change);
        var previous = value.subtract(delta);
        var changeRate = delta.multiply(new BigDecimal("100")).divide(previous, 3, RoundingMode.HALF_UP);
        Market market = switch (code) {
            case "KOSPI", "KOSDAQ" -> Market.KRX;
            case "SPX", "IXIC" -> Market.US;
            default -> throw new IllegalArgumentException("Unknown index: " + code);
        };
        return new IndexItem(code, name, value, delta, changeRate, FIXED_MOCK_DATA_TIME,
                ZonedDateTime.ofInstant(FIXED_MOCK_DATA_TIME, SEOUL), SEOUL.getId(),
                ExchangeTime.atExchange(market, FIXED_MOCK_DATA_TIME), ExchangeTime.zoneFor(market).getId(),
                "mock", status);
    }

    public record QuotesResponse(List<QuoteItem> items, ZonedDateTime screenRefreshedAt, String displayTimezone) { }
    public record QuoteItem(SecurityCatalog.Security security, BigDecimal price, BigDecimal change,
                            BigDecimal changeRate, String source, Instant sourceTimestamp,
                            ZonedDateTime displayTimestamp, String displayTimezone,
                            ZonedDateTime exchangeTimestamp, String exchangeTimezone,
                            Instant retrievedAt, String status) { }
    public record IndicesResponse(List<IndexItem> items, ZonedDateTime screenRefreshedAt) { }
    public record IndexItem(String index, String name, BigDecimal value, BigDecimal change,
                            BigDecimal changeRate, Instant sourceTimestamp, ZonedDateTime displayTimestamp,
                            String displayTimezone, ZonedDateTime exchangeTimestamp,
                            String exchangeTimezone, String source, String status) { }
    public record NewsResponse(List<NewsItem> items, ZonedDateTime screenRefreshedAt) { }
    public record NewsItem(String id, Market market, String symbol, String title, String source,
                           Instant publishedAt, ZonedDateTime displayPublishedAt, String displayTimezone,
                           String url, String provider) { }
}
