package com.scopesky.paymentservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scopesky.paymentservice.dto.PaymentRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Base class for all integration tests.
 * <p>
 * Boots the full Spring context with an in-memory H2 database and wires up
 * MockMvc so sub-classes can fire HTTP requests against the running
 * application.  The {@code @Transactional} annotation ensures every test runs
 * in its own transaction that is rolled back automatically when the test
 * finishes, keeping the database clean between tests.
 * </p>
 *
 * <p>Extend this class for any test that needs the full application context:</p>
 * <pre>{@code
 * class MyControllerTest extends BaseIntegrationTest { ... }
 * }</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    protected PaymentRequest buildRequest(BigDecimal amount, String status) {
        return new PaymentRequest(amount, status);
    }

    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
