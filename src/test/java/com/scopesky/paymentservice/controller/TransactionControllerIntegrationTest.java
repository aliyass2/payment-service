package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.BaseIntegrationTest;
import com.scopesky.paymentservice.dto.wallet.PayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionControllerIntegrationTest extends BaseIntegrationTest {

    private long walletId;
    private long depositTxnId;

    @BeforeEach
    void setUp() throws Exception {
        long userId = createUserViaApi("Alice", "Smith", "alice@example.com");
        String walletsJson = mockMvc.perform(get("/api/users/" + userId + "/wallets"))
                .andReturn().getResponse().getContentAsString();
        walletId = objectMapper.readTree(walletsJson).get(0).get("id").asLong();
        depositTxnId = depositViaApi(walletId, "300.00", "setup-txn-1");
    }

    @Test
    void getTransactionById_existingId_returns200() throws Exception {
        mockMvc.perform(get("/api/transactions/" + depositTxnId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(depositTxnId))
                .andExpect(jsonPath("$.type").value("DEPOSIT"));
    }

    @Test
    void getTransactionById_nonExistingId_returns404() throws Exception {
        mockMvc.perform(get("/api/transactions/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransactionByReferenceId_existingRef_returns200() throws Exception {
        String txnJson = mockMvc.perform(get("/api/transactions/" + depositTxnId))
                .andReturn().getResponse().getContentAsString();
        String ref = objectMapper.readTree(txnJson).get("referenceId").asText();

        mockMvc.perform(get("/api/transactions/ref/" + ref))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value(ref));
    }

    @Test
    void getTransactionsByWallet_returnsAllTransactions() throws Exception {
        depositViaApi(walletId, "100.00", "setup-txn-2");
        mockMvc.perform(get("/api/wallets/" + walletId + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getTransactionsByWallet_withTypeFilter_returnsFilteredTransactions() throws Exception {
        PayRequest payReq = new PayRequest(new BigDecimal("50.00"), "MERCH-1", null, "pay-txn-1");
        mockMvc.perform(post("/api/wallets/" + walletId + "/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(payReq)));

        mockMvc.perform(get("/api/wallets/" + walletId + "/transactions?type=PAYMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("PAYMENT"));
    }

    @Test
    void reverseTransaction_completedPayment_returns200WithReversalType() throws Exception {
        PayRequest payReq = new PayRequest(new BigDecimal("100.00"), "MERCH-2", null, "pay-txn-rev1");
        String payJson = mockMvc.perform(post("/api/wallets/" + walletId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payReq)))
                .andReturn().getResponse().getContentAsString();
        long payTxnId = objectMapper.readTree(payJson).get("id").asLong();

        mockMvc.perform(post("/api/transactions/" + payTxnId + "/reverse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("REVERSAL"))
                .andExpect(jsonPath("$.relatedTransactionId").value(payTxnId));
    }

    @Test
    void reverseTransaction_alreadyReversed_returns400() throws Exception {
        PayRequest payReq = new PayRequest(new BigDecimal("50.00"), "MERCH-3", null, "pay-txn-rev2");
        String payJson = mockMvc.perform(post("/api/wallets/" + walletId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payReq)))
                .andReturn().getResponse().getContentAsString();
        long payTxnId = objectMapper.readTree(payJson).get("id").asLong();

        mockMvc.perform(post("/api/transactions/" + payTxnId + "/reverse"));
        mockMvc.perform(post("/api/transactions/" + payTxnId + "/reverse"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reverseTransaction_nonExistingId_returns404() throws Exception {
        mockMvc.perform(post("/api/transactions/999999/reverse"))
                .andExpect(status().isNotFound());
    }
}
