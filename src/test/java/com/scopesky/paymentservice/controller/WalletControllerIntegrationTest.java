package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.BaseIntegrationTest;
import com.scopesky.paymentservice.dto.wallet.*;
import com.scopesky.paymentservice.model.enums.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WalletControllerIntegrationTest extends BaseIntegrationTest {

    private long userId;
    private long walletId;

    @BeforeEach
    void setUp() throws Exception {
        userId = createUserViaApi("Alice", "Smith", "alice@example.com");
        // createUserViaApi auto-creates a default wallet; fetch it
        String walletsJson = mockMvc.perform(get("/api/users/" + userId + "/wallets"))
                .andReturn().getResponse().getContentAsString();
        walletId = objectMapper.readTree(walletsJson).get(0).get("id").asLong();
    }

    // --- Deposit ---

    @Test
    void deposit_validRequest_returns200WithCompletedTransaction() throws Exception {
        DepositRequest req = new DepositRequest(new BigDecimal("100.00"), "Initial deposit", "key-dep-1");
        mockMvc.perform(post("/api/wallets/" + walletId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.balanceBefore").value(0))
                .andExpect(jsonPath("$.balanceAfter").value(100.00))
                .andExpect(jsonPath("$.referenceId").value(org.hamcrest.Matchers.startsWith("TXN-")));
    }

    @Test
    void deposit_frozenWallet_returns409() throws Exception {
        mockMvc.perform(put("/api/wallets/" + walletId + "/freeze"));
        DepositRequest req = new DepositRequest(new BigDecimal("50.00"), null, "key-dep-2");
        mockMvc.perform(post("/api/wallets/" + walletId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void deposit_zeroAmount_returns400() throws Exception {
        DepositRequest req = new DepositRequest(new BigDecimal("0.00"), null, "key-dep-3");
        mockMvc.perform(post("/api/wallets/" + walletId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_duplicateIdempotencyKey_returns409() throws Exception {
        DepositRequest req = new DepositRequest(new BigDecimal("50.00"), null, "key-dup-1");
        mockMvc.perform(post("/api/wallets/" + walletId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/wallets/" + walletId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict());
    }

    // --- Withdraw ---

    @Test
    void withdraw_sufficientBalance_returns200() throws Exception {
        depositViaApi(walletId, "200.00", "key-setup-w1");
        WithdrawRequest req = new WithdrawRequest(new BigDecimal("50.00"), "Withdrawal", "key-wdr-1");
        mockMvc.perform(post("/api/wallets/" + walletId + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balanceAfter").value(150.00));
    }

    @Test
    void withdraw_insufficientBalance_returns422() throws Exception {
        WithdrawRequest req = new WithdrawRequest(new BigDecimal("100.00"), null, "key-wdr-2");
        mockMvc.perform(post("/api/wallets/" + walletId + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Insufficient")));
    }

    @Test
    void withdraw_frozenWallet_returns409() throws Exception {
        depositViaApi(walletId, "200.00", "key-setup-w2");
        mockMvc.perform(put("/api/wallets/" + walletId + "/freeze"));
        WithdrawRequest req = new WithdrawRequest(new BigDecimal("50.00"), null, "key-wdr-3");
        mockMvc.perform(post("/api/wallets/" + walletId + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict());
    }

    // --- Pay ---

    @Test
    void pay_sufficientBalance_returns200WithPaymentType() throws Exception {
        depositViaApi(walletId, "500.00", "key-setup-p1");
        PayRequest req = new PayRequest(new BigDecimal("99.99"), "MERCHANT-001", "Coffee", "key-pay-1");
        mockMvc.perform(post("/api/wallets/" + walletId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PAYMENT"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void pay_insufficientBalance_returns422() throws Exception {
        PayRequest req = new PayRequest(new BigDecimal("999.00"), "MERCHANT-002", null, "key-pay-2");
        mockMvc.perform(post("/api/wallets/" + walletId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pay_frozenWallet_returns409() throws Exception {
        depositViaApi(walletId, "500.00", "key-setup-p2");
        mockMvc.perform(put("/api/wallets/" + walletId + "/freeze"));
        PayRequest req = new PayRequest(new BigDecimal("10.00"), "MERCHANT-003", null, "key-pay-3");
        mockMvc.perform(post("/api/wallets/" + walletId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict());
    }

    // --- Refund ---

    @Test
    void refund_validOriginalPayment_returns200WithRefundType() throws Exception {
        depositViaApi(walletId, "500.00", "key-setup-r1");
        PayRequest payReq = new PayRequest(new BigDecimal("100.00"), "MERCHANT-REF", null, "key-pay-r1");
        String payJson = mockMvc.perform(post("/api/wallets/" + walletId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payReq)))
                .andReturn().getResponse().getContentAsString();
        long origId = objectMapper.readTree(payJson).get("id").asLong();

        RefundRequest refReq = new RefundRequest(origId, new BigDecimal("50.00"), "Partial refund", "key-ref-1");
        mockMvc.perform(post("/api/wallets/" + walletId + "/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(refReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("REFUND"))
                .andExpect(jsonPath("$.relatedTransactionId").value(origId));
    }

    @Test
    void refund_originalTransactionNotFound_returns404() throws Exception {
        RefundRequest refReq = new RefundRequest(999999L, new BigDecimal("10.00"), null, "key-ref-2");
        mockMvc.perform(post("/api/wallets/" + walletId + "/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(refReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    void refund_amountExceedsOriginal_returns400() throws Exception {
        depositViaApi(walletId, "500.00", "key-setup-r2");
        PayRequest payReq = new PayRequest(new BigDecimal("50.00"), "MERCHANT-REF2", null, "key-pay-r2");
        String payJson = mockMvc.perform(post("/api/wallets/" + walletId + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(payReq)))
                .andReturn().getResponse().getContentAsString();
        long origId = objectMapper.readTree(payJson).get("id").asLong();

        RefundRequest refReq = new RefundRequest(origId, new BigDecimal("100.00"), null, "key-ref-3");
        mockMvc.perform(post("/api/wallets/" + walletId + "/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(refReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exceeds")));
    }

    @Test
    void refund_nonPaymentTransaction_returns400() throws Exception {
        long depositTxnId = depositViaApi(walletId, "100.00", "key-setup-r3");
        RefundRequest refReq = new RefundRequest(depositTxnId, new BigDecimal("10.00"), null, "key-ref-4");
        mockMvc.perform(post("/api/wallets/" + walletId + "/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(refReq)))
                .andExpect(status().isBadRequest());
    }

    // --- Freeze / Unfreeze ---

    @Test
    void freezeWallet_activeWallet_returns200WithFrozenStatus() throws Exception {
        mockMvc.perform(put("/api/wallets/" + walletId + "/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    void unfreezeWallet_frozenWallet_returns200WithActiveStatus() throws Exception {
        mockMvc.perform(put("/api/wallets/" + walletId + "/freeze"));
        mockMvc.perform(put("/api/wallets/" + walletId + "/unfreeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void freezeWallet_alreadyFrozen_returns400() throws Exception {
        mockMvc.perform(put("/api/wallets/" + walletId + "/freeze"));
        mockMvc.perform(put("/api/wallets/" + walletId + "/freeze"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unfreezeWallet_activeWallet_returns400() throws Exception {
        mockMvc.perform(put("/api/wallets/" + walletId + "/unfreeze"))
                .andExpect(status().isBadRequest());
    }

    // --- Create wallet ---

    @Test
    void createWallet_validRequest_returns201() throws Exception {
        CreateWalletRequest req = new CreateWalletRequest(userId, Currency.EUR, "Savings");
        mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.referenceId").value(org.hamcrest.Matchers.startsWith("WLT-")));
    }

    @Test
    void createWallet_nonExistingUser_returns404() throws Exception {
        CreateWalletRequest req = new CreateWalletRequest(999999L, Currency.USD, null);
        mockMvc.perform(post("/api/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getWalletById_nonExisting_returns404() throws Exception {
        mockMvc.perform(get("/api/wallets/999999"))
                .andExpect(status().isNotFound());
    }
}
