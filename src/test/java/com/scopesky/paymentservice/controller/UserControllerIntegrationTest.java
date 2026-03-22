package com.scopesky.paymentservice.controller;

import com.scopesky.paymentservice.BaseIntegrationTest;
import com.scopesky.paymentservice.dto.user.CreateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE_URL = "/api/users";

    @Test
    void createUser_validRequest_returns201WithBody() throws Exception {
        CreateUserRequest req = new CreateUserRequest("Alice", "Smith", "alice@example.com", "+1234567890");
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.referenceId").value(org.hamcrest.Matchers.startsWith("USR-")))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createUser_duplicateEmail_returns400() throws Exception {
        createUserViaApi("Alice", "Smith", "alice@example.com");
        CreateUserRequest req = new CreateUserRequest("Bob", "Jones", "alice@example.com", null);
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already registered")));
    }

    @Test
    void createUser_blankFirstName_returns400() throws Exception {
        CreateUserRequest req = new CreateUserRequest("", "Smith", "bob@example.com", null);
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        CreateUserRequest req = new CreateUserRequest("Bob", "Smith", "not-an-email", null);
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserById_existingId_returns200() throws Exception {
        long id = createUserViaApi("Alice", "Smith", "alice@example.com");
        mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getUserById_nonExistingId_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("999999")));
    }

    @Test
    void getUserByReferenceId_existingRef_returns200() throws Exception {
        long id = createUserViaApi("Alice", "Smith", "alice@example.com");
        String ref = objectMapper.readTree(
                mockMvc.perform(get(BASE_URL + "/" + id)).andReturn().getResponse().getContentAsString()
        ).get("referenceId").asText();

        mockMvc.perform(get(BASE_URL + "/ref/" + ref))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value(ref));
    }

    @Test
    void getUserByReferenceId_nonExistingRef_returns404() throws Exception {
        mockMvc.perform(get(BASE_URL + "/ref/USR-NOTEXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    void suspendUser_activeUser_returns200WithSuspendedStatus() throws Exception {
        long id = createUserViaApi("Alice", "Smith", "alice@example.com");
        mockMvc.perform(put(BASE_URL + "/" + id + "/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void suspendUser_alreadySuspended_returns400() throws Exception {
        long id = createUserViaApi("Alice", "Smith", "alice@example.com");
        mockMvc.perform(put(BASE_URL + "/" + id + "/suspend"));
        mockMvc.perform(put(BASE_URL + "/" + id + "/suspend"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspendUser_nonExistingUser_returns404() throws Exception {
        mockMvc.perform(put(BASE_URL + "/999999/suspend"))
                .andExpect(status().isNotFound());
    }

    @Test
    void activateUser_suspendedUser_returns200WithActiveStatus() throws Exception {
        long id = createUserViaApi("Alice", "Smith", "alice@example.com");
        mockMvc.perform(put(BASE_URL + "/" + id + "/suspend"));
        mockMvc.perform(put(BASE_URL + "/" + id + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void activateUser_alreadyActive_returns400() throws Exception {
        long id = createUserViaApi("Alice", "Smith", "alice@example.com");
        mockMvc.perform(put(BASE_URL + "/" + id + "/activate"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activateUser_nonExistingUser_returns404() throws Exception {
        mockMvc.perform(put(BASE_URL + "/999999/activate"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllUsers_returnsAllCreatedUsers() throws Exception {
        createUserViaApi("Alice", "Smith", "alice@example.com");
        createUserViaApi("Bob", "Jones", "bob@example.com");
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
