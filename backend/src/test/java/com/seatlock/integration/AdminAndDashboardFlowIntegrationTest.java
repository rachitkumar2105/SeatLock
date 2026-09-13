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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// See AuthFlowIntegrationTest for why this hits the docker-compose Postgres instead of Testcontainers.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AdminAndDashboardFlowIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void organizerCanSeeOwnEventsAndStats() throws Exception {
        HttpHeaders organizerHeaders = registerOrganizerAndLogin();
        String eventId = createPublishedEvent(organizerHeaders);
        createSeats(eventId, organizerHeaders);

        ResponseEntity<String> mineResponse = restTemplate.exchange(
                "/api/events/mine", HttpMethod.GET, new HttpEntity<>(organizerHeaders), String.class
        );
        assertThat(mineResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode mineBody = objectMapper.readTree(mineResponse.getBody());
        assertThat(mineBody.get("content").isArray()).isTrue();
        boolean containsCreatedEvent = false;
        for (JsonNode e : mineBody.get("content")) {
            if (e.get("id").asText().equals(eventId)) containsCreatedEvent = true;
        }
        assertThat(containsCreatedEvent).isTrue();

        ResponseEntity<String> statsResponse = restTemplate.exchange(
                "/api/events/" + eventId + "/stats", HttpMethod.GET, new HttpEntity<>(organizerHeaders), String.class
        );
        assertThat(statsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode stats = objectMapper.readTree(statsResponse.getBody());
        assertThat(stats.get("totalSeats").asLong()).isEqualTo(4);
        assertThat(stats.get("availableSeats").asLong()).isEqualTo(4);
    }

    @Test
    void nonOwnerOrganizerCannotSeeAnotherOrganizersStats() throws Exception {
        HttpHeaders ownerHeaders = registerOrganizerAndLogin();
        String eventId = createPublishedEvent(ownerHeaders);

        HttpHeaders otherOrganizerHeaders = registerOrganizerAndLogin();
        ResponseEntity<String> statsResponse = restTemplate.exchange(
                "/api/events/" + eventId + "/stats", HttpMethod.GET, new HttpEntity<>(otherOrganizerHeaders), String.class
        );
        assertThat(statsResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanListUsersUpdateRolesModerateEventsAndReadMetrics() throws Exception {
        HttpHeaders adminHeaders = registerAdminAndLogin();

        String plainUserEmail = "dash-target-" + UUID.randomUUID() + "@example.com";
        register(plainUserEmail, "correct-horse-battery", "Dash Target");
        String plainUserId = userRepository.findByEmail(plainUserEmail).orElseThrow().getId().toString();

        // List users
        ResponseEntity<String> usersResponse = restTemplate.exchange(
                "/api/admin/users", HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class
        );
        assertThat(usersResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Promote plain user to ORGANIZER via admin API
        HttpHeaders jsonAdminHeaders = new HttpHeaders(adminHeaders);
        jsonAdminHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> roleUpdateResponse = restTemplate.exchange(
                "/api/admin/users/" + plainUserId + "/role",
                HttpMethod.PATCH,
                new HttpEntity<>("{\"role\":\"ORGANIZER\"}", jsonAdminHeaders),
                String.class
        );
        assertThat(roleUpdateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(roleUpdateResponse.getBody()).get("role").asText()).isEqualTo("ORGANIZER");

        // Moderate (cancel) an event as admin
        String eventId = createPublishedEvent(adminHeaders);
        ResponseEntity<String> cancelResponse = restTemplate.postForEntity(
                "/api/admin/events/" + eventId + "/cancel", new HttpEntity<>(null, adminHeaders), String.class
        );
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(cancelResponse.getBody()).get("status").asText()).isEqualTo("CANCELLED");

        // Metrics
        ResponseEntity<String> metricsResponse = restTemplate.exchange(
                "/api/admin/metrics", HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class
        );
        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode metrics = objectMapper.readTree(metricsResponse.getBody());
        assertThat(metrics.get("totalUsers").asLong()).isGreaterThan(0);
    }

    @Test
    void plainUserCannotAccessAdminEndpoints() throws Exception {
        HttpHeaders userHeaders = registerUserAndLogin();
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/users", HttpMethod.GET, new HttpEntity<>(userHeaders), String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders registerOrganizerAndLogin() throws Exception {
        String email = "dash-organizer-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        register(email, password, "Dash Organizer");
        promote(email, Role.ORGANIZER);
        return authHeadersFor(email, password);
    }

    private HttpHeaders registerAdminAndLogin() throws Exception {
        String email = "dash-admin-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        register(email, password, "Dash Admin");
        promote(email, Role.ADMIN);
        return authHeadersFor(email, password);
    }

    private HttpHeaders registerUserAndLogin() throws Exception {
        String email = "dash-user-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        register(email, password, "Dash User");
        return authHeadersFor(email, password);
    }

    private void promote(String email, Role role) {
        var user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(role);
        userRepository.save(user);
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
                {"title":"Dashboard Test Concert","description":"d","venueName":"Arena","eventDate":"%s"}
                """.formatted(eventDate);
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                "/api/events", new HttpEntity<>(createBody, headers), String.class
        );
        String eventId = objectMapper.readTree(createResponse.getBody()).get("id").asText();

        restTemplate.postForEntity("/api/events/" + eventId + "/publish", new HttpEntity<>(null, headers), String.class);
        return eventId;
    }

    private void createSeats(String eventId, HttpHeaders organizerAuthHeaders) {
        HttpHeaders headers = new HttpHeaders(organizerAuthHeaders);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String createSeatsBody = """
                {"seats":[
                    {"section":"A","row":"1","number":1,"price":50.00},
                    {"section":"A","row":"1","number":2,"price":50.00},
                    {"section":"A","row":"1","number":3,"price":50.00},
                    {"section":"A","row":"1","number":4,"price":50.00}
                ]}
                """;
        restTemplate.postForEntity("/api/events/" + eventId + "/seats", new HttpEntity<>(createSeatsBody, headers), String.class);
    }
}
