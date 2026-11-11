package com.priyani.wallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level tests: status codes, validation and JSON shape. */
@SpringBootTest
@AutoConfigureMockMvc
class WalletApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions transfer(String key, long from, long to, String amount) throws Exception {
        return mvc.perform(post("/api/transfers")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromUserId\":" + from + ",\"toUserId\":" + to + ",\"amount\":" + amount + "}"));
    }

    private long newUser() throws Exception {
        String body = postJson("/api/users",
                "{\"name\":\"Test\",\"email\":\"" + UUID.randomUUID() + "@test.com\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        postJson("/api/users", "{\"name\":\"A\",\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String body = "{\"name\":\"A\",\"email\":\"" + UUID.randomUUID() + "@test.com\"}";
        postJson("/api/users", body).andExpect(status().isCreated());
        postJson("/api/users", body).andExpect(status().isConflict());
    }

    @Test
    void depositUpdatesBalance() throws Exception {
        long id = newUser();
        postJson("/api/users/" + id + "/deposit", "{\"amount\":100.00}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"));
        mvc.perform(get("/api/users/" + id + "/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.0));
    }

    @Test
    void depositRejectsNonPositiveAmount() throws Exception {
        long id = newUser();
        postJson("/api/users/" + id + "/deposit", "{\"amount\":0}")
                .andExpect(status().isBadRequest());
        postJson("/api/users/" + id + "/deposit", "{\"amount\":-5}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserReturns404() throws Exception {
        mvc.perform(get("/api/users/999999/wallet")).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/999999/transactions")).andExpect(status().isNotFound());
    }

    @Test
    void transferRequiresIdempotencyKey() throws Exception {
        long a = newUser(), b = newUser();
        postJson("/api/transfers",
                "{\"fromUserId\":" + a + ",\"toUserId\":" + b + ",\"amount\":1.00}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferWithInsufficientBalanceReturns422() throws Exception {
        long a = newUser(), b = newUser();
        transfer(UUID.randomUUID().toString(), a, b, "5.00")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transferToSelfIsRejected() throws Exception {
        long a = newUser();
        transfer(UUID.randomUUID().toString(), a, a, "1.00")
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferToUnknownUserReturns404() throws Exception {
        long a = newUser();
        postJson("/api/users/" + a + "/deposit", "{\"amount\":10.00}");
        transfer(UUID.randomUUID().toString(), a, 999999L, "1.00")
                .andExpect(status().isNotFound());
    }

    @Test
    void replayedTransferReturnsSameResultAndChargesOnce() throws Exception {
        long a = newUser(), b = newUser();
        postJson("/api/users/" + a + "/deposit", "{\"amount\":100.00}");
        String key = UUID.randomUUID().toString();

        String first = transfer(key, a, b, "40.00").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String retry = transfer(key, a, b, "40.00").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
                json.readTree(first).get("id"), json.readTree(retry).get("id"));
        mvc.perform(get("/api/users/" + a + "/wallet")).andExpect(jsonPath("$.balance").value(60.0));
        mvc.perform(get("/api/users/" + b + "/wallet")).andExpect(jsonPath("$.balance").value(40.0));
    }

    @Test
    void reusingKeyWithDifferentAmountIsRejected() throws Exception {
        long a = newUser(), b = newUser();
        postJson("/api/users/" + a + "/deposit", "{\"amount\":100.00}");
        String key = UUID.randomUUID().toString();

        transfer(key, a, b, "10.00").andExpect(status().isCreated());
        transfer(key, a, b, "99.00").andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/users/" + a + "/wallet")).andExpect(jsonPath("$.balance").value(90.0));
    }

    @Test
    void historyIsPaginatedNewestFirst() throws Exception {
        long id = newUser();
        for (int i = 1; i <= 3; i++) {
            postJson("/api/users/" + id + "/deposit", "{\"amount\":" + i + ".00}");
        }

        mvc.perform(get("/api/users/" + id + "/transactions?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].amount").value(3.0))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(get("/api/users/" + id + "/transactions?page=1&size=2"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(1.0));
    }

    @Test
    void historyRejectsBadPaging() throws Exception {
        long id = newUser();
        mvc.perform(get("/api/users/" + id + "/transactions?size=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/" + id + "/transactions?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/" + id + "/transactions?page=-1")).andExpect(status().isBadRequest());
    }
}
