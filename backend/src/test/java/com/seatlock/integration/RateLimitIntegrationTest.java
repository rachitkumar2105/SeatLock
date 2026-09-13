package com.seatlock.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// See AuthFlowIntegrationTest for why this hits the docker-compose Postgres instead of Testcontainers.
//
// Every other integration test disables rate limiting (see their class-level comments) to avoid
// conflating unrelated test traffic with the abuse pattern the limiter exists to catch. This test
// is the one place it stays on, with a tiny limit, specifically to prove the limiter itself works.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login.limit=3",
        "app.rate-limit.login.window-seconds=60"
})
class RateLimitIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exceedingTheLoginLimitReturns429WithRetryAfter() {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String loginBody = """
                {"email":"nobody@example.com","password":"wrong-password"}
                """;

        HttpStatusCode lastStatus = null;
        ResponseEntity<String> lastResponse = null;
        for (int i = 0; i < 5; i++) {
            lastResponse = restTemplate.postForEntity("/api/auth/login", new HttpEntity<>(loginBody, jsonHeaders), String.class);
            lastStatus = lastResponse.getStatusCode();
            if (lastStatus == HttpStatus.TOO_MANY_REQUESTS) break;
        }

        assertThat(lastStatus).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(lastResponse.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }
}
