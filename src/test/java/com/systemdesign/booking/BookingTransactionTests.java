package com.systemdesign.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:database/schema.sql"
})
class BookingTransactionTests {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("booking_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long showId;
    private long firstSeatId;
    private long secondSeatId;

    @BeforeEach
    void createFixtures() {
        Long eventId = jdbcTemplate.queryForObject(
                """
                INSERT INTO events (name, description)
                VALUES ('Transaction test', 'Integration test fixture')
                RETURNING id
                """,
                Long.class
        );

        showId = jdbcTemplate.queryForObject(
                """
                INSERT INTO shows (event_id, starts_at, capacity)
                VALUES (?, CURRENT_TIMESTAMP, 10)
                RETURNING id
                """,
                Long.class,
                eventId
        );

        firstSeatId = insertSeat("A1");
        secondSeatId = insertSeat("A2");
    }

    @Test
    void rollsBackEarlierBookingWhenAnotherSeatIsAlreadyBooked() {
        // Commit a pre-existing booking for the second seat.
        Booking original =
                bookingService.createBooking(showId, secondSeatId);

        // First seat is inserted before the second seat conflicts.
        assertThrows(
                SeatAlreadyBookedException.class,
                () -> bookingService.createGroupBooking(
                        showId,
                        List.of(firstSeatId, secondSeatId)
                )
        );

        // The group's earlier insert must have been rolled back.
        assertEquals(0L, countBookings(firstSeatId));

        // The booking committed before the group still exists.
        assertEquals(1L, countBookings(secondSeatId));

        Long remainingBookingId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM bookings
                WHERE show_seat_id = ?
                """,
                Long.class,
                secondSeatId
        );

        assertEquals(original.id(), remainingBookingId.longValue());
    }

    @Test
    void commitsBothBookingsWhenBothSeatsAreAvailable() {
        List<Booking> created = bookingService.createGroupBooking(
                showId,
                List.of(firstSeatId, secondSeatId)
        );

        assertEquals(2, created.size());
        assertEquals(1L, countBookings(firstSeatId));
        assertEquals(1L, countBookings(secondSeatId));
    }

    private long insertSeat(String label) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO show_seats (show_id, seat_label)
                VALUES (?, ?)
                RETURNING id
                """,
                Long.class,
                showId,
                label
        );
    }

    private long countBookings(long seatId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM bookings
                WHERE show_seat_id = ?
                """,
                Long.class,
                seatId
        );

        return count == null ? 0L : count;
    }
}