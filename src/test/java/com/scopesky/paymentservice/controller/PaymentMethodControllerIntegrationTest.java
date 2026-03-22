package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.BaseIntegrationTest;
import com.scopesky.paymentservice.dto.paymentmethod.AddPaymentMethodRequest;
import com.scopesky.paymentservice.model.enums.PaymentMethodType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentMethodControllerIntegrationTest extends BaseIntegrationTest {

    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        userId = createUserViaApi("Alice", "Smith", "alice@example.com");
    }

    private long addMethod(boolean isDefault, String masked) throws Exception {
        AddPaymentMethodRequest req = new AddPaymentMethodRequest(
                userId, PaymentMethodType.CARD, "Visa", masked, "12/28", isDefault);
        String json = mockMvc.perform(post("/api/payment-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void addPaymentMethod_validRequest_returns201() throws Exception {
        AddPaymentMethodRequest req = new AddPaymentMethodRequest(
                userId, PaymentMethodType.CARD, "Mastercard", "1234", "11/27", false);
        mockMvc.perform(post("/api/payment-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceId").value(org.hamcrest.Matchers.startsWith("PMT-")))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void addPaymentMethod_isDefaultTrue_unsetsExistingDefault() throws Exception {
        long first = addMethod(true, "1111");
        long second = addMethod(true, "2222");

        mockMvc.perform(get("/api/payment-methods/" + first))
                .andExpect(jsonPath("$.default").value(false));
        mockMvc.perform(get("/api/payment-methods/" + second))
                .andExpect(jsonPath("$.default").value(true));
    }

    @Test
    void addPaymentMethod_nonExistingUser_returns404() throws Exception {
        AddPaymentMethodRequest req = new AddPaymentMethodRequest(
                999999L, PaymentMethodType.CARD, "Visa", "9999", null, false);
        mockMvc.perform(post("/api/payment-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addPaymentMethod_missingProvider_returns400() throws Exception {
        AddPaymentMethodRequest req = new AddPaymentMethodRequest(
                userId, PaymentMethodType.CARD, "", "1234", null, false);
        mockMvc.perform(post("/api/payment-methods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setDefault_validMethod_returns200AndPreviousUnset() throws Exception {
        long first = addMethod(true, "1111");
        long second = addMethod(false, "2222");

        mockMvc.perform(put("/api/payment-methods/" + second + "/set-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.default").value(true));

        mockMvc.perform(get("/api/payment-methods/" + first))
                .andExpect(jsonPath("$.default").value(false));
    }

    @Test
    void setDefault_nonExistingId_returns404() throws Exception {
        mockMvc.perform(put("/api/payment-methods/999999/set-default"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatePaymentMethod_existingId_returns204() throws Exception {
        long id = addMethod(false, "3333");
        mockMvc.perform(delete("/api/payment-methods/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/payment-methods/" + id))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivatePaymentMethod_nonExistingId_returns404() throws Exception {
        mockMvc.perform(delete("/api/payment-methods/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPaymentMethodsByUser_returnsActiveMethods() throws Exception {
        long id1 = addMethod(false, "1111");
        long id2 = addMethod(false, "2222");
        mockMvc.perform(delete("/api/payment-methods/" + id1));

        mockMvc.perform(get("/api/users/" + userId + "/payment-methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id2));
    }
}
