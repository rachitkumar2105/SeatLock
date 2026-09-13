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
class BookingFlowIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void lockThenBookThenRetryWithSameIdempotencyKeyReturnsSameBooking() throws Exception {
        HttpHeaders organizerHeaders = registerOrganizerAndLogin();
        String eventId = createPublishedEvent(organizerHeaders);
        String seatId = createSingleSeat(eventId, organizerHeaders);

        HttpHeaders buyerHeaders = registerUserAndLogin();

        ResponseEntity<String> lockResponse = restTemplate.postForEntity(
                "/api/seats/" + seatId + "/lock", new HttpEntity<>(null, buyerHeaders), String.class
        );
        assertThat(lockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders bookingHeaders = new HttpHeaders(buyerHeaders);
        bookingHeaders.setContentType(MediaType.APPLICATION_JSON);
        String idempotencyKey = UUID.randomUUID().toString();
        bookingHeaders.add("Idempotency-Key", idempotencyKey);

        String bookingBody = """
                {"eventId":"%s","seatIds":["%s"]}
                """.formatted(eventId, seatId);

        ResponseEntity<String> firstBookingResponse = restTemplate.postForEntity(
                "/api/bookings", new HttpEntity<>(bookingBody, bookingHeaders), String.class
        );
        assertThat(firstBookingResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bookingId = objectMapper.readTree(firstBookingResponse.getBody()).get("id").asText();

        // Retrying with the same Idempotency-Key must return the original booking, not create a new one
        // or fail because the seat is no longer LOCKED.
        ResponseEntity<String> retryResponse = restTemplate.postForEntity(
                "/api/bookings", new HttpEntity<>(bookingBody, bookingHeaders), String.class
        );
        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(retryResponse.getBody()).get("id").asText()).isEqualTo(bookingId);
    }

    @Test
    void cannotBookASeatThatIsNotLockedByYou() throws Exception {
        HttpHeaders organizerHeaders = registerOrganizerAndLogin();
        String eventId = createPublishedEvent(organizerHeaders);
        String seatId = createSingleSeat(eventId, organizerHeaders);

        HttpHeaders buyerHeaders = registerUserAndLogin();
        HttpHeaders bookingHeaders = new HttpHeaders(buyerHeaders);
        bookingHeaders.setContentType(MediaType.APPLICATION_JSON);
        bookingHeaders.add("Idempotency-Key", UUID.randomUUID().toString());

        String bookingBody = """
                {"eventId":"%s","seatIds":["%s"]}
                """.formatted(eventId, seatId);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/bookings", new HttpEntity<>(bookingBody, bookingHeaders), String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void releasingALockMakesTheSeatAvailableAgain() throws Exception {
        HttpHeaders organizerHeaders = registerOrganizerAndLogin();
        String eventId = createPublishedEvent(organizerHeaders);
        String seatId = createSingleSeat(eventId, organizerHeaders);

        HttpHeaders buyerHeaders = registerUserAndLogin();

        restTemplate.postForEntity("/api/seats/" + seatId + "/lock", new HttpEntity<>(null, buyerHeaders), String.class);

        ResponseEntity<Void> releaseResponse = restTemplate.postForEntity(
                "/api/seats/" + seatId + "/release", new HttpEntity<>(null, buyerHeaders), Void.class
        );
        assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        HttpHeaders otherBuyerHeaders = registerUserAndLogin();
        ResponseEntity<String> secondLockResponse = restTemplate.postForEntity(
                "/api/seats/" + seatId + "/lock", new HttpEntity<>(null, otherBuyerHeaders), String.class
        );
        assertThat(secondLockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpHeaders registerOrganizerAndLogin() throws Exception {
        String email = "booking-organizer-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        register(email, password, "Booking Organizer");

        var user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(Role.ORGANIZER);
        userRepository.save(user);

        return authHeadersFor(email, password);
    }

    private HttpHeaders registerUserAndLogin() throws Exception {
        String email = "buyer-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        register(email, password, "Buyer");
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
                {"title":"Booking Flow Concert","description":"d","venueName":"Arena","eventDate":"%s"}
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
