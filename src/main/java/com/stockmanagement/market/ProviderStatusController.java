package com.stockmanagement.market;

import com.stockmanagement.auth.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProviderStatusController {
    private final ProviderStatusService statuses;
    private final AuthService auth;

    public ProviderStatusController(ProviderStatusService statuses, AuthService auth) {
        this.statuses = statuses;
        this.auth = auth;
    }

    @GetMapping("/api/v1/provider-status")
    public Response statuses(@RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.userId(authorization);
        return new Response(statuses.findAll());
    }

    public record Response(List<ProviderStatusService.ProviderStatus> items) { }
}
