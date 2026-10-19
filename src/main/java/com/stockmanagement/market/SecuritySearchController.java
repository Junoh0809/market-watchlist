package com.stockmanagement.market;

import com.stockmanagement.auth.AuthService;
import com.stockmanagement.watchlist.Market;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/securities")
public class SecuritySearchController {
    private final AuthService auth;
    private final SecurityCatalog catalog;
    public SecuritySearchController(AuthService auth, SecurityCatalog catalog) { this.auth = auth; this.catalog = catalog; }

    @GetMapping
    public SearchResponse search(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestParam("market") Market market, @RequestParam(name = "query", defaultValue = "") String query,
                                 @RequestParam(name = "limit", defaultValue = "20") int limit) {
        auth.userId(authorization);
        var safeLimit = Math.max(1, Math.min(limit, 50));
        return new SearchResponse(catalog.search(market, query, safeLimit), safeLimit);
    }

    public record SearchResponse(List<SecurityCatalog.Security> items, int limit) { }
}
