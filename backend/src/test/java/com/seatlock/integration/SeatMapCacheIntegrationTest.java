package com.seatlock.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatlock.entity.Role;
import com.seatlock.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// See AuthFlowIntegrationTest for why this hits the docker-compose Postgres instead of Testcontainers.
//
// Caching the seat-map read (GET /api/events/{id}/seats) is exactly the kind of change that could
// quietly reintroduce a staleness bug — this proves that locking a seat is reflected on the very
// next read, not delayed until the cache TTL expires. The real invalidation path is the explicit
// evict in SeatBroadcastPublisher, not the TTL (which is just a backstop).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "app.rate-limit.enabled=false")
class SeatMapCacheIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void lockingASeatIsVisibleOnTheVeryNextReadDespiteCaching() throws Exception {
        HttpHeaders organizerHeaders = registerOrganizerAndLogin();
        String eventId = createPublishedEvent(organizerHeaders);
        String seatId = createSingleSeat(eventId, organizerHeaders);

        // Warm the cache.
        JsonNode before = objectMapper.readTree(
                restTemplate.getForEntity("/api/events/" + eventId + "/seats", String.class).getBody()
        );
        assertThat(before.get(0).get("status").asText()).isEqualTo("AVAILABLE");

        HttpHeaders buyerHeaders = registerUserAndLogin();
        ResponseEntity<String> lockResponse = restTemplate.postForEntity(
                "/api/seats/" + seatId + "/lock", new HttpEntity<>(null, buyerHeaders), String.class
        );
        assertThat(lockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode after = objectMapper.readTree(
                restTemplate.getForEntity("/api/events/" + eventId + "/seats", String.class).getBody()
        );
        assertThat(after.get(0).get("status").asText()).isEqualTo("LOCKED");
    }

    private HttpHeaders registerOrganizerAndLogin() throws Exception {
        String email = "cache-organizer-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        register(email, password, "Cache Organizer");

        var user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(Role.ORGANIZER);
        userRepository.save(user);

        return authHeadersFor(email, password);
    }

    private HttpHeaders registerUserAndLogin() throws Exception {
        String email = "cache-buyer-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        register(email, password, "Cache Buyer");
        return authHeadersFor(email, password);
    }

    private void register(String email, String password, String name) {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String registerBody = """
                {"name":"%s","email":"%s","password":"%s"}
                """.formatted(name, email, password);
        restTemplate.postForEntity("/api/auth/register", new HttpEntity<>(registerBody, jsonHeaders), String.class);
    }

    private HttpHeaders authHeadersFor(String email, String password) throws Exception {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String loginBody = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginBody, jsonHeaders), String.class
        );
        String accessToken = objectMapper.readTree(loginResponse.getBody()).get("accessToken").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private String createPublishedEvent(HttpHeaders organizerAuthHeaders) throws Exception {
        HttpHeaders headers = new HttpHeaders(organizerAuthHeaders);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String eventDate = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String createBody = """
                {"title":"Cache Test Concert","description":"d","venueName":"Arena","eventDate":"%s"}
                """.formatted(eventDate);
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                "/api/events", new HttpEntity<>(createBody, headers), String.class
        );
        String eventId = objectMapper.readTree(createResponse.getBody()).get("id").asText();

        restTemplate.postForEntity("/api/events/" + eventId + "/publish", new HttpEntity<>(null, headers), String.class);
        return eventId;
    }

    private String createSingleSeat(String eventId, HttpHeaders organizerAuthHeaders) throws Exception {
        HttpHeaders headers = new HttpHeaders(organizerAuthHeaders);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String createSeatsBody = """
                {"seats":[{"section":"A","row":"1","number":1,"price":50.00}]}
                """;
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/events/" + eventId + "/seats", new HttpEntity<>(createSeatsBody, headers), String.class
        );
        JsonNode seats = objectMapper.readTree(response.getBody());
        return seats.get(0).get("id").asText();
    }
}
