package com.stockmanagement.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final Duration SESSION_LIFETIME = Duration.ofDays(7);
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Account signup(String email, String password) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        var account = new Account(UUID.randomUUID().toString(), normalized, hashPassword(password));
        try {
            jdbc.update("INSERT INTO accounts (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)",
                    account.id(), account.email(), account.passwordHash(), Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists", exception);
        }
        return account;
    }

    public String login(String email, String password) {
        var matches = jdbc.query("SELECT id, email, password_hash FROM accounts WHERE email = ?",
                (rs, row) -> new Account(rs.getString("id"), rs.getString("email"), rs.getString("password_hash")),
                email.trim().toLowerCase(Locale.ROOT));
        if (matches.isEmpty() || !passwordMatches(password, matches.getFirst().passwordHash()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        jdbc.update("INSERT INTO sessions (token_hash, account_id, expires_at) VALUES (?, ?, ?)",
                tokenHash(token), matches.getFirst().id(), Timestamp.from(Instant.now().plus(SESSION_LIFETIME)));
        return token;
    }

    public String userId(String authorization) {
        String hash = tokenHash(bearerToken(authorization));
        var matches = jdbc.query("SELECT account_id FROM sessions WHERE token_hash = ? AND expires_at > ?",
                (rs, row) -> rs.getString("account_id"), hash, Timestamp.from(Instant.now()));
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        return matches.getFirst();
    }

    public void logout(String authorization) {
        userId(authorization);
        jdbc.update("DELETE FROM sessions WHERE token_hash = ?", tokenHash(bearerToken(authorization)));
    }

    @Transactional
    public void delete(String authorization) {
        String id = userId(authorization);
        // Foreign keys remove this account's sessions and watchlist items atomically.
        jdbc.update("DELETE FROM accounts WHERE id = ?", id);
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.substring(7).isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required");
        return authorization.substring(7);
    }

    private static String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Token hashing is unavailable", exception);
        }
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derivePassword(password, salt));
    }

    private static boolean passwordMatches(String password, String stored) {
        String[] parts = stored.split(":", 2);
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        return MessageDigest.isEqual(expected, derivePassword(password, salt));
    }

    private static byte[] derivePassword(String password, byte[] salt) {
        var spec = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            spec.clearPassword();
        }
    }

    public record Account(String id, String email, String passwordHash) { }
}
