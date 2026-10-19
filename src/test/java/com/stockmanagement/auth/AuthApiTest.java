package com.stockmanagement.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.stockmanagement.watchlist.WatchlistService watchlist;
    @Autowired com.stockmanagement.market.SecurityCatalog catalog;

    @Test
    void accountSessionAndWatchlistSurviveServiceRecreation() {
        String email = UUID.randomUUID() + "@example.com";
        var original = new AuthService(jdbc);
        var account = original.signup(email, "password-123");
        String token = original.login(email, "password-123");
        watchlist.add(account.id(), new com.stockmanagement.watchlist.WatchlistRequest(
                com.stockmanagement.watchlist.Market.US, "AAPL"));

        var restartedAuth = new AuthService(jdbc);
        var restartedWatchlist = new com.stockmanagement.watchlist.WatchlistService(jdbc, catalog);
        org.junit.jupiter.api.Assertions.assertEquals(account.id(), restartedAuth.userId("Bearer " + token));
        org.junit.jupiter.api.Assertions.assertEquals("AAPL", restartedWatchlist.findAll(account.id()).getFirst().symbol());
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM sessions WHERE token_hash = ?", Integer.class, token));

        restartedAuth.delete("Bearer " + token);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> original.userId("Bearer " + token));
        org.junit.jupiter.api.Assertions.assertTrue(restartedWatchlist.findAll(account.id()).isEmpty());
    }

    @Test
    void expiredSessionIsRejected() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var account = auth().signup(email, "password-123");
        String token = auth().login(email, "password-123");
        jdbc.update("UPDATE sessions SET expires_at = ? WHERE account_id = ?",
                java.sql.Timestamp.from(java.time.Instant.EPOCH), account.id());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> auth().userId("Bearer " + token));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM sessions WHERE account_id = ? AND expires_at > ?", Integer.class,
                account.id(), java.sql.Timestamp.from(java.time.Instant.now())));
        mvc.perform(get("/api/v1/me/watchlist").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    private AuthService auth() { return new AuthService(jdbc); }

    @Test
    void accountDeletionRemovesWatchlistAndInvalidatesSessions() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        signup(email);
        String first = login(email);
        String second = login(email);
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"market\":\"US\",\"symbol\":\"AAPL\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + first))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/watchlist").header("Authorization", "Bearer " + second))
                .andExpect(status().isUnauthorized());
        signup(email);
        String replacement = login(email);
        mvc.perform(get("/api/v1/me/watchlist").header("Authorization", "Bearer " + replacement))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void logoutRequiresValidSessionAndReturnsNoContent() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
        String email = UUID.randomUUID() + "@example.com";
        signup(email);
        String token = login(email);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/watchlist").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private void signup(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthController.Credentials(email, "password-123"))))
                .andExpect(status().isOk());
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthController.Credentials(email, "password-123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
}
