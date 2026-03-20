# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean install

# Run
./mvnw spring-boot:run

# Test (all)
./mvnw test

# Test (single class)
./mvnw test -Dtest=ClassName

# Test (single method)
./mvnw test -Dtest=ClassName#methodName

# Package JAR
./mvnw clean package
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Architecture

Spring Boot 3.5.11 / Java 21 REST service under `com.scopesky.paymentservice`.

**Key dependencies:**
- `spring-boot-starter-web` — REST controllers
- `spring-boot-starter-data-jpa` — JPA repositories
- `spring-boot-starter-validation` — Bean validation (`@Valid`, constraint annotations)
- `h2` (runtime) — In-memory database (dev/test)
- `lombok` — Boilerplate reduction (`@Data`, `@Builder`, etc.)

**Standard layering:** Controllers → Services → Repositories (JPA) → H2 database.

Config lives in `src/main/resources/application.yaml`. Test config overrides in `src/test/resources/application.yaml`.

## Testing Rules

**Every new endpoint MUST have corresponding tests. This is non-negotiable.**

### Test structure

| Layer | Class | Extends |
|---|---|---|
| Integration (HTTP) | `*ControllerIntegrationTest` | `BaseIntegrationTest` |
| Unit (service logic) | `*ServiceTest` | — (Mockito only) |

`BaseIntegrationTest` lives at `src/test/java/com/scopesky/paymentservice/BaseIntegrationTest.java`.
It boots the full Spring context with H2 + MockMvc and rolls back after every test via `@Transactional`.

### Checklist for each new controller endpoint

When adding a new endpoint (e.g. `POST /api/payments/{id}/refund`), you MUST:

1. Add the controller method in `PaymentController`.
2. Add the service method in `PaymentService`.
3. Add integration tests in the matching `*ControllerIntegrationTest` that extend `BaseIntegrationTest`:
   - Happy path (2xx response + response body assertions)
   - Not-found / error path (4xx response + error message assertions)
   - Validation path if the endpoint accepts a request body (400 for invalid input)
4. Add unit tests in `PaymentServiceTest` (or the matching `*ServiceTest`) using Mockito:
   - Happy path
   - Not-found / exception path
   - Edge cases specific to the business logic

### Example pattern for a new endpoint

```java
// Controller test (extends BaseIntegrationTest)
@Test
void refundPayment_existingId_returns200() throws Exception {
    long id = createPaymentViaApi("100.00", "COMPLETED");
    mockMvc.perform(post(BASE_URL + "/" + id + "/refund"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"));
}

@Test
void refundPayment_nonExistingId_returns404() throws Exception {
    mockMvc.perform(post(BASE_URL + "/999/refund"))
            .andExpect(status().isNotFound());
}

// Service unit test (Mockito, no Spring context)
@Test
void refundPayment_existingId_updatesStatus() { ... }

@Test
void refundPayment_nonExistingId_throwsPaymentNotFoundException() { ... }
```
