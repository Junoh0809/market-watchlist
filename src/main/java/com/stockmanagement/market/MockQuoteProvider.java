package com.stockmanagement.market;

import com.stockmanagement.watchlist.Market;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;

@Component
public class MockQuoteProvider implements QuoteProvider {
    private static final Map<String, BigDecimal> BASE_PRICES = Map.of(
            "KRX:005930", new BigDecimal("72000.00"),
            "KRX:000660", new BigDecimal("250000.00"),
            "KRX:035420", new BigDecimal("245000.00"),
            "US:AAPL", new BigDecimal("225.12"),
            "US:MSFT", new BigDecimal("510.00"),
            "US:NVDA", new BigDecimal("145.00"),
            "US:AMZN", new BigDecimal("225.00")
    );

    enum Scenario { NORMAL, DELAYED, FAILURE, UPDATING }

    private volatile Scenario scenario;

    @Autowired
    public MockQuoteProvider(@Value("${stock.mock.quote-scenario:NORMAL}") String scenario) {
        this.scenario = Scenario.valueOf(scenario.toUpperCase(Locale.ROOT));
    }

    MockQuoteProvider(Scenario scenario) { this.scenario = scenario; }

    void setScenario(Scenario scenario) { this.scenario = scenario; }

    @Override
    public String name() { return "mock"; }

    @Override
    public QuoteSnapshot fetch(SecurityCatalog.Security security, Instant collectedAt) {
        if (scenario == Scenario.FAILURE) throw new Unavailable("Mock quote provider failed");
        if (scenario == Scenario.UPDATING) throw new Updating("Mock quote refresh is in progress");
        Instant sourceTime = collectedAt.truncatedTo(ChronoUnit.MINUTES);
        if (scenario == Scenario.DELAYED) sourceTime = sourceTime.minus(5, ChronoUnit.MINUTES);

        var base = BASE_PRICES.get(security.market() + ":" + security.symbol());
        if (base == null) throw new Unavailable("Mock quote is unavailable");
        var movementUnit = security.market() == Market.KRX ? new BigDecimal("500.00") : new BigDecimal("0.25");
        long step = Math.floorMod(sourceTime.getEpochSecond() / 60, 20);
        var price = base.add(movementUnit.multiply(BigDecimal.valueOf(step))).setScale(2, RoundingMode.HALF_UP);
        var change = price.subtract(base);
        var changeRate = change.multiply(new BigDecimal("100")).divide(base, 3, RoundingMode.HALF_UP);
        return new QuoteSnapshot(price, change, changeRate, sourceTime, collectedAt);
    }
}
