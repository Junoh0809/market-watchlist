package com.stockmanagement.watchlist;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/watchlist")
public class WatchlistController {
    private final WatchlistService service;
    private final com.stockmanagement.auth.AuthService auth;

    public WatchlistController(WatchlistService service, com.stockmanagement.auth.AuthService auth) { this.service = service; this.auth = auth; }

    @GetMapping
    public ItemsResponse list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return new ItemsResponse(service.findAll(auth.userId(authorization)));
    }

    @PostMapping
    public WatchlistItem add(@RequestHeader(value = "Authorization", required = false) String authorization,
                             @Valid @RequestBody WatchlistRequest request) {
        return service.add(auth.userId(authorization), request);
    }

    @DeleteMapping("/{market}/{symbol}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @PathVariable("market") Market market, @PathVariable("symbol") String symbol) {
        service.delete(auth.userId(authorization), market, symbol);
    }

    public record ItemsResponse(List<WatchlistItem> items) { }
}
