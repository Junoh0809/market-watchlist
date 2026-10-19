package com.stockmanagement.watchlist;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WatchlistControllerTest {
    @Autowired MockMvc mvc;
    @Autowired com.stockmanagement.auth.AuthService auth;
    @Autowired com.stockmanagement.market.ProviderStatusService providerStatuses;
    @Autowired ObjectMapper objectMapper;

    @Test
    void usersOnlySeeTheirOwnWatchlist() throws Exception {
        String alice = token("alice@example.com", "pw1");
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"market\":\"US\",\"symbol\":\"AAPL\"}"))
                .andExpect(status().isOk());
        String bob = token("bob@example.com", "pw2");
        mvc.perform(get("/api/v1/me/watchlist").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/me/watchlist").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].symbol").value("AAPL"));
    }

    @Test
    void duplicateItemsAreRejected() throws Exception {
        String body = "{\"market\":\"KRX\",\"symbol\":\"005930\"}";
        String alice = token("duplicate@example.com", "pw");
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void aUserCannotDeleteAnotherUsersItem() throws Exception {
        String alice = token("owner@example.com", "pw");
        String bob = token("other@example.com", "pw");
        String body = "{\"market\":\"US\",\"symbol\":\"MSFT\"}";
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/me/watchlist/US/MSFT").header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/watchlist").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].symbol").value("MSFT"));
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me/watchlist")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAnItemReturnsNoContent() throws Exception {
        String token = token("delete-item@example.com", "pw");
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"market\":\"US\",\"symbol\":\"AAPL\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/me/watchlist/US/AAPL").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownSecurityCannotBeWatched() throws Exception {
        String token = token("unknown-security@example.com", "pw");
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"market\":\"US\",\"symbol\":\"FAKE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SECURITY_NOT_FOUND"));
    }

    @Test
    void securitiesCanBeSearchedByMarketAndQuery() throws Exception {
        String token = token("search@example.com", "pw");
        mvc.perform(get("/api/v1/securities?market=US&query=apple&limit=20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].symbol").value("AAPL"));
    }

    @Test
    void mockQuoteIsSharedAndStableBetweenRequests() throws Exception {
        String token = token("quote-refresh@example.com", "pw");
        String other = token("quote-refresh-other@example.com", "pw");
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"market\":\"US\",\"symbol\":\"AAPL\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"market\":\"US\",\"symbol\":\"AAPL\"}"))
                .andExpect(status().isOk());
        var first = objectMapper.readTree(mvc.perform(get("/api/v1/me/quotes")
                        .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        var second = objectMapper.readTree(mvc.perform(get("/api/v1/me/quotes")
                        .header("Authorization", "Bearer " + other)).andReturn().getResponse().getContentAsString());
        var firstSource = java.time.Instant.parse(first.at("/items/0/sourceTimestamp").asText());
        var secondSource = java.time.Instant.parse(second.at("/items/0/sourceTimestamp").asText());
        org.junit.jupiter.api.Assertions.assertTrue(firstSource.equals(secondSource)
                || secondSource.equals(firstSource.plusSeconds(60)));
        if (firstSource.equals(secondSource))
            org.junit.jupiter.api.Assertions.assertEquals(first.at("/items/0/price"), second.at("/items/0/price"));
        org.junit.jupiter.api.Assertions.assertTrue(first.at("/items/0/security/userId").isMissingNode());
        var providerStatus = providerStatuses.findAll().stream()
                .filter(item -> item.market() == Market.US && item.symbol().equals("AAPL"))
                .findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(providerStatus.lastSuccessAt());
        mvc.perform(get("/api/v1/provider-status")).andExpect(status().isUnauthorized());
    }

    @Test
    void KoreanSecurityUsesWonAndRealisticMockPrice() throws Exception {
        String token = token("krx-price@example.com", "pw");
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"market\":\"KRX\",\"symbol\":\"005930\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/quotes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].security.currency").value("KRW"))
                .andExpect(jsonPath("$.items[0].exchangeTimezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.items[0].price").value(org.hamcrest.Matchers.greaterThan(50000.0)));
    }

    @Test
    void newsFollowsOnlyTheAuthenticatedUsersWatchlist() throws Exception {
        String alice = token("news-owner@example.com", "pw");
        String bob = token("news-other@example.com", "pw");
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"market\":\"US\",\"symbol\":\"AAPL\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/news").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/me/news").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.items[0].displayTimezone").value("Asia/Seoul"));
    }

    @Test
    void fixedMockIndexDoesNotClaimNewSourceTimeOnEveryRead() throws Exception {
        String token = token("index-time@example.com", "pw");
        var first = objectMapper.readTree(mvc.perform(get("/api/v1/market-indices")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        var second = objectMapper.readTree(mvc.perform(get("/api/v1/market-indices")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(first.at("/items/0/sourceTimestamp"),
                second.at("/items/0/sourceTimestamp"));
        org.junit.jupiter.api.Assertions.assertEquals("Asia/Seoul",
                first.at("/items/0/displayTimezone").asText());
    }

    private String token(String email, String password) {
        auth.signup(email, password);
        return auth.login(email, password);
    }
}
