package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.BaseIntegrationTest;
import com.scopesky.paymentservice.dto.PaymentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/payments";

    // -----------------------------------------------------------------------
    // POST /api/payments
    // -----------------------------------------------------------------------

    @Test
    void createPayment_validRequest_returns201WithBody() throws Exception {
        PaymentRequest request = buildRequest(new BigDecimal("100.00"), "PENDING");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void createPayment_amountBelowMinimum_returns400() throws Exception {
        PaymentRequest request = buildRequest(new BigDecimal("0.00"), "PENDING");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("amount")));
    }

    @Test
    void createPayment_nullAmount_returns400() throws Exception {
        PaymentRequest request = buildRequest(null, "PENDING");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_blankStatus_returns400() throws Exception {
        PaymentRequest request = buildRequest(new BigDecimal("50.00"), "");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("status")));
    }

    // -----------------------------------------------------------------------
    // GET /api/payments
    // -----------------------------------------------------------------------

    @Test
    void getAllPayments_emptyDatabase_returnsEmptyList() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getAllPayments_afterCreating_returnsAllPayments() throws Exception {
        createPaymentViaApi("150.00", "COMPLETED");
        createPaymentViaApi("75.50", "PENDING");

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // -----------------------------------------------------------------------
    // GET /api/payments/{id}
    // -----------------------------------------------------------------------

    @Test
    void getPaymentById_existingId_returns200WithBody() throws Exception {
        long id = createPaymentViaApi("200.00", "PENDING");

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getPaymentById_nonExistingId_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Payment not found with id: 999"));
    }

    // -----------------------------------------------------------------------
    // PUT /api/payments/{id}
    // -----------------------------------------------------------------------

    @Test
    void updatePayment_existingId_returns200WithUpdatedValues() throws Exception {
        long id = createPaymentViaApi("100.00", "PENDING");
        PaymentRequest update = buildRequest(new BigDecimal("250.00"), "COMPLETED");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void updatePayment_nonExistingId_returns404() throws Exception {
        PaymentRequest update = buildRequest(new BigDecimal("100.00"), "COMPLETED");

        mockMvc.perform(put(BASE_URL + "/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(update)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePayment_invalidBody_returns400() throws Exception {
        long id = createPaymentViaApi("100.00", "PENDING");
        PaymentRequest invalid = buildRequest(new BigDecimal("-1.00"), "COMPLETED");

        mockMvc.perform(put(BASE_URL + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(invalid)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // DELETE /api/payments/{id}
    // -----------------------------------------------------------------------

    @Test
    void deletePayment_existingId_returns204() throws Exception {
        long id = createPaymentViaApi("75.00", "PENDING");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deletePayment_existingId_subsequentGetReturns404() throws Exception {
        long id = createPaymentViaApi("75.00", "PENDING");

        mockMvc.perform(delete(BASE_URL + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePayment_nonExistingId_returns404() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/999"))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------
    // Private helper
    // -----------------------------------------------------------------------

    private long createPaymentViaApi(String amount, String status) throws Exception {
        PaymentRequest request = buildRequest(new BigDecimal(amount), status);
        String responseBody = mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody).get("id").asLong();
    }
}
