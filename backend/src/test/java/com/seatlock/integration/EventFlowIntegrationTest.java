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
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// See AuthFlowIntegrationTest for why this hits the docker-compose Postgres instead of Testcontainers.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
// Many rapid logins from one test-JVM "IP" across dozens of test methods is expected test
// traffic, not the abuse pattern the rate limiter exists to catch (verified separately).
@TestPropertySource(properties = "app.rate-limit.enabled=false")
class EventFlowIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void organizerCanCreateAndPublishAnEvent() throws Exception {
        String email = "organizer-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        String registerBody = """
                {"name":"Org Test","email":"%s","password":"%s"}
                """.formatted(email, password);
        restTemplate.postForEntity("/api/auth/register", new HttpEntity<>(registerBody, jsonHeaders), String.class);

        var user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(Role.ORGANIZER);
        userRepository.save(user);

        String loginBody = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginBody, jsonHeaders), String.class
        );
        String accessToken = objectMapper.readTree(loginResponse.getBody()).get("accessToken").asText();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(accessToken);

        String eventDate = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String createBody = """
                {"title":"Test Concert","description":"A test event","venueName":"Test Arena","eventDate":"%s"}
                """.formatted(eventDate);
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                "/api/events", new HttpEntity<>(createBody, authHeaders), String.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode created = objectMapper.readTree(createResponse.getBody());
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        String eventId = created.get("id").asText();

        ResponseEntity<String> publishResponse = restTemplate.postForEntity(
                "/api/events/" + eventId + "/publish", new HttpEntity<>(null, authHeaders), String.class
        );
        assertThat(publishResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(publishResponse.getBody()).get("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    void plainUserCannotCreateAnEvent() {
        String email = "plain-user-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";

        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

        String registerBody = """
                {"name":"Plain User","email":"%s","password":"%s"}
                """.formatted(email, password);
        restTemplate.postForEntity("/api/auth/register", new HttpEntity<>(registerBody, jsonHeaders), String.class);

        String loginBody = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginBody, jsonHeaders), String.class
        );

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(extractAccessToken(loginResponse));

        String eventDate = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String createBody = """
                {"title":"Test Concert","description":"A test event","venueName":"Test Arena","eventDate":"%s"}
                """.formatted(eventDate);
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                "/api/events", new HttpEntity<>(createBody, authHeaders), String.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String extractAccessToken(ResponseEntity<String> loginResponse) {
        try {
            return objectMapper.readTree(loginResponse.getBody()).get("accessToken").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
