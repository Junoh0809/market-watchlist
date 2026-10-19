package com.stockmanagement.market;

import com.stockmanagement.auth.AuthService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "stock.mock.quote-scenario=FAILURE")
@AutoConfigureMockMvc
class QuoteFailureApiTest {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired ProviderStatusService statuses;

    @Test
    void firstProviderFailureReturnsUpdatingWithoutInventingAQuote() throws Exception {
        String email = "first-quote-failure@example.com";
        auth.signup(email, "test-password");
        String token = auth.login(email, "test-password");
        mvc.perform(post("/api/v1/me/watchlist").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"market\":\"US\",\"symbol\":\"AAPL\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/quotes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("UPDATING"))
                .andExpect(jsonPath("$.items[0].price").value(nullValue()));
        mvc.perform(get("/api/v1/provider-status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        var failed = statuses.findAll().stream().filter(item -> item.symbol().equals("AAPL"))
                .findFirst().orElseThrow();
        Assertions.assertEquals(QuoteService.QuoteStatus.UPDATING, failed.status());
        Assertions.assertNotNull(failed.lastFailureAt());
    }
}
