package com.stockmanagement.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/auth/signup")
    public SignupResponse signup(@Valid @RequestBody Credentials request) {
        var account = service.signup(request.email(), request.password());
        return new SignupResponse(account.email());
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody Credentials request) { return new TokenResponse(service.login(request.email(), request.password())); }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) { service.logout(authorization); }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value = "Authorization", required = false) String authorization) { service.delete(authorization); }

    public record Credentials(@Email @NotBlank @Size(max = 320) String email,
                              @NotBlank @Size(max = 256) String password) { }
    public record SignupResponse(String email) { }
    public record TokenResponse(String accessToken) { }
}
