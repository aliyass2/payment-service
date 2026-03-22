package com.scopesky.paymentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scopesky.paymentservice.dto.user.CreateUserRequest;
import com.scopesky.paymentservice.dto.wallet.CreateWalletRequest;
import com.scopesky.paymentservice.dto.wallet.DepositRequest;
import com.scopesky.paymentservice.model.enums.Currency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for all integration tests.
 * Boots the full Spring context with an in-memory H2 database and wires up
 * MockMvc. {@code @Transactional} rolls back after every test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    protected long createUserViaApi(String firstName, String lastName, String email) throws Exception {
        CreateUserRequest req = new CreateUserRequest(firstName, lastName, email, null);
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    protected long createWalletViaApi(long userId, Currency currency) throws Exception {
        CreateWalletRequest req = new CreateWalletRequest(userId, currency, null);
        MvcResult result = mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    protected long depositViaApi(long walletId, String amount, String idempotencyKey) throws Exception {
        DepositRequest req = new DepositRequest(new BigDecimal(amount), null, idempotencyKey);
        MvcResult result = mockMvc.perform(post("/api/wallets/" + walletId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
