package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.BaseIntegrationTest;
import com.scopesky.paymentservice.dto.transfer.TransferRequest;
import com.scopesky.paymentservice.model.enums.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransferControllerIntegrationTest extends BaseIntegrationTest {

    private long userId1;
    private long userId2;
    private long walletA;
    private long walletB;

    @BeforeEach
    void setUp() throws Exception {
        userId1 = createUserViaApi("Alice", "Smith", "alice@example.com");
        userId2 = createUserViaApi("Bob", "Jones", "bob@example.com");

        String walletsA = mockMvc.perform(get("/api/users/" + userId1 + "/wallets"))
                .andReturn().getResponse().getContentAsString();
        walletA = objectMapper.readTree(walletsA).get(0).get("id").asLong();

        String walletsB = mockMvc.perform(get("/api/users/" + userId2 + "/wallets"))
                .andReturn().getResponse().getContentAsString();
        walletB = objectMapper.readTree(walletsB).get(0).get("id").asLong();

        depositViaApi(walletA, "500.00", "setup-transfer-1");
    }

    @Test
    void transfer_validRequest_returns201WithTransferResponse() throws Exception {
        TransferRequest req = new TransferRequest(walletA, walletB, new BigDecimal("100.00"), "Payment for goods", "trf-key-1");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceId").value(org.hamcrest.Matchers.startsWith("TRF-")))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.debitTransactionReferenceId").isString())
                .andExpect(jsonPath("$.creditTransactionReferenceId").isString());
    }

    @Test
    void transfer_sourceInsufficientFunds_returns422() throws Exception {
        TransferRequest req = new TransferRequest(walletA, walletB, new BigDecimal("9999.00"), null, "trf-key-2");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transfer_sameWallet_returns400() throws Exception {
        TransferRequest req = new TransferRequest(walletA, walletA, new BigDecimal("10.00"), null, "trf-key-3");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_currencyMismatch_returns409() throws Exception {
        long eurWalletId = createWalletViaApi(userId2, Currency.EUR);
        TransferRequest req = new TransferRequest(walletA, eurWalletId, new BigDecimal("10.00"), null, "trf-key-4");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("mismatch")));
    }

    @Test
    void transfer_sourceWalletFrozen_returns409() throws Exception {
        mockMvc.perform(put("/api/wallets/" + walletA + "/freeze"));
        TransferRequest req = new TransferRequest(walletA, walletB, new BigDecimal("10.00"), null, "trf-key-5");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void transfer_destinationWalletFrozen_returns409() throws Exception {
        mockMvc.perform(put("/api/wallets/" + walletB + "/freeze"));
        TransferRequest req = new TransferRequest(walletA, walletB, new BigDecimal("10.00"), null, "trf-key-6");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void transfer_duplicateIdempotencyKey_returns409() throws Exception {
        TransferRequest req = new TransferRequest(walletA, walletB, new BigDecimal("10.00"), null, "trf-key-dup");
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void getTransferById_existingId_returns200() throws Exception {
        TransferRequest req = new TransferRequest(walletA, walletB, new BigDecimal("10.00"), null, "trf-key-get");
        String json = mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(json).get("id").asLong();

        mockMvc.perform(get("/api/transfers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getTransferById_nonExistingId_returns404() throws Exception {
        mockMvc.perform(get("/api/transfers/999999"))
                .andExpect(status().isNotFound());
    }
}
