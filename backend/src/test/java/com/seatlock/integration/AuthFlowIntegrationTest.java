package com.seatlock.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against the docker-compose Postgres (see application.yml) rather than a Testcontainers-managed
// instance: Testcontainers can't reach Docker Desktop's npipe on this machine without enabling the
// unauthenticated TCP daemon, which we're deliberately not doing. Revisit before the Phase 2 concurrency test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthFlowIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registerLoginRefreshAndLogoutFlowWorks() throws Exception {
        String email = "flow-test-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        String registerBody = """
                {"name":"Flow Test","email":"%s","password":"%s"}
                """.formatted(email, password);
        ResponseEntity<String> registerResponse = restTemplate.postForEntity(
                "/api/auth/register", new HttpEntity<>(registerBody, jsonHeaders), String.class
        );
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String loginBody = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginBody, jsonHeaders), String.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> setCookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        String refreshCookie = extractCookie(setCookies, "refresh_token");
        String csrfCookie = extractCookie(setCookies, "csrf_token");
        assertThat(refreshCookie).isNotBlank();
        assertThat(csrfCookie).isNotBlank();

        JsonNode loginJson = objectMapper.readTree(loginResponse.getBody());
        assertThat(loginJson.get("accessToken").asText()).isNotBlank();

        // Simulate "stay logged in across a refresh": use the refresh cookie to mint a new access token.
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.add(HttpHeaders.COOKIE, "refresh_token=" + refreshCookie + "; csrf_token=" + csrfCookie);
        refreshHeaders.add("X-CSRF-Token", csrfCookie);
        ResponseEntity<String> refreshResponse = restTemplate.postForEntity(
                "/api/auth/refresh", new HttpEntity<>(null, refreshHeaders), String.class
        );
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> refreshSetCookies = refreshResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        String rotatedRefreshCookie = extractCookie(refreshSetCookies, "refresh_token");
        String rotatedCsrfCookie = extractCookie(refreshSetCookies, "csrf_token");
        assertThat(rotatedRefreshCookie).isNotEqualTo(refreshCookie);

        // The old refresh token must now be rejected (rotation).
        ResponseEntity<String> reuseOldResponse = restTemplate.postForEntity(
                "/api/auth/refresh", new HttpEntity<>(null, refreshHeaders), String.class
        );
        assertThat(reuseOldResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.add(HttpHeaders.COOKIE, "refresh_token=" + rotatedRefreshCookie + "; csrf_token=" + rotatedCsrfCookie);
        logoutHeaders.add("X-CSRF-Token", rotatedCsrfCookie);
        ResponseEntity<String> logoutResponse = restTemplate.postForEntity(
                "/api/auth/logout", new HttpEntity<>(null, logoutHeaders), String.class
        );
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        String email = "wrong-pass-" + UUID.randomUUID() + "@example.com";

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        String registerBody = """
                {"name":"Wrong Pass","email":"%s","password":"correct-horse-battery"}
                """.formatted(email);
        restTemplate.postForEntity("/api/auth/register", new HttpEntity<>(registerBody, jsonHeaders), String.class);

        String loginBody = """
                {"email":"%s","password":"totally-wrong"}
                """.formatted(email);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginBody, jsonHeaders), String.class
        );
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String extractCookie(List<String> setCookieHeaders, String cookieName) {
        return setCookieHeaders.stream()
                .filter(h -> h.startsWith(cookieName + "="))
                .findFirst()
                .map(h -> h.substring(cookieName.length() + 1).split(";", 2)[0])
                .orElse(null);
    }
}
