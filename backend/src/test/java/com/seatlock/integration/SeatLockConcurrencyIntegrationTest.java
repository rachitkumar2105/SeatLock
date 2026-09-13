package com.seatlock.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatlock.entity.Role;
import com.seatlock.entity.SeatStatus;
import com.seatlock.repository.SeatRepository;
import com.seatlock.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// See AuthFlowIntegrationTest for why this hits the docker-compose Postgres instead of Testcontainers.
//
// This is the test the blueprint calls the single highest-value thing in the whole project: N
// concurrent lock requests race for the same seat, and exactly one must win.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SeatLockConcurrencyIntegrationTest {

    private static final int CONCURRENT_USERS = 25;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeatRepository seatRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exactlyOneConcurrentLockRequestSucceedsForTheSameSeat() throws Exception {
        HttpHeaders organizerAuthHeaders = registerOrganizerAndLogin();
        String eventId = createPublishedEvent(organizerAuthHeaders);
        String seatId = createSingleSeat(eventId, organizerAuthHeaders);

        List<String> accessTokens = registerAndLoginUsers(CONCURRENT_USERS);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_USERS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HttpStatusCode>> futures = new ArrayList<>();

        for (String token : accessTokens) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(token);
                ResponseEntity<String> response = restTemplate.postForEntity(
                        "/api/seats/" + seatId + "/lock", new HttpEntity<>(null, headers), String.class
                );
                return response.getStatusCode();
            }));
        }

        ready.await(10, TimeUnit.SECONDS);
        go.countDown();

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        for (Future<HttpStatusCode> future : futures) {
            HttpStatusCode status = future.get(30, TimeUnit.SECONDS);
            if (status.equals(HttpStatus.OK)) {
                successCount.incrementAndGet();
            } else if (status.equals(HttpStatus.CONFLICT)) {
                conflictCount.incrementAndGet();
            }
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(CONCURRENT_USERS - 1);

        var seat = seatRepository.findById(UUID.fromString(seatId)).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(seat.getLockedBy()).isNotNull();
    }

    private List<String> registerAndLoginUsers(int count) throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String email = "racer-" + UUID.randomUUID() + "@example.com";
            String password = "correct-horse-battery";
            register(email, password, "Racer");
            tokens.add(login(email, password));
        }
        return tokens;
    }

    private HttpHeaders registerOrganizerAndLogin() throws Exception {
        String email = "concurrency-organizer-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        register(email, password, "Concurrency Organizer");

        var user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(Role.ORGANIZER);
        userRepository.save(user);

        String accessToken = login(email, password);
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.setBearerAuth(accessToken);
        return authHeaders;
    }

    private void register(String email, String password, String name) {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String registerBody = """
                {"name":"%s","email":"%s","password":"%s"}
                """.formatted(name, email, password);
        restTemplate.postForEntity("/api/auth/register", new HttpEntity<>(registerBody, jsonHeaders), String.class);
    }

    private String login(String email, String password) throws Exception {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        String loginBody = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new HttpEntity<>(loginBody, jsonHeaders), String.class
        );
        return objectMapper.readTree(loginResponse.getBody()).get("accessToken").asText();
    }

    private String createPublishedEvent(HttpHeaders organizerAuthHeaders) throws Exception {
        String eventDate = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        String createBody = """
                {"title":"Concurrency Test Concert","description":"d","venueName":"Arena","eventDate":"%s"}
                """.formatted(eventDate);
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                "/api/events", new HttpEntity<>(createBody, organizerAuthHeaders), String.class
        );
        String eventId = objectMapper.readTree(createResponse.getBody()).get("id").asText();

        restTemplate.postForEntity(
                "/api/events/" + eventId + "/publish", new HttpEntity<>(null, organizerAuthHeaders), String.class
        );
        return eventId;
    }

    private String createSingleSeat(String eventId, HttpHeaders organizerAuthHeaders) throws Exception {
        String createSeatsBody = """
                {"seats":[{"section":"A","row":"1","number":1,"price":50.00}]}
                """;
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/events/" + eventId + "/seats", new HttpEntity<>(createSeatsBody, organizerAuthHeaders), String.class
        );
        JsonNode seats = objectMapper.readTree(response.getBody());
        return seats.get(0).get("id").asText();
    }
}
