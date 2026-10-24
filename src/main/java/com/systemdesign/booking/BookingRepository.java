package com.systemdesign.booking;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {
    private final JdbcTemplate jdbcTemplate;

    public BookingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean seatBelongsToShow(long showSeatId, long showId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM show_seats
                WHERE id = ? AND show_id = ?
                """,
                Long.class,
                showSeatId,
                showId
        );

        return count != null && count > 0;
    }

    public Booking createBooking(long showSeatId) {
        var inserted = jdbcTemplate.query(
                """
                INSERT INTO bookings (show_seat_id)
                VALUES (?)
                ON CONFLICT (show_seat_id) DO NOTHING
                RETURNING id, show_seat_id
                """,
                (rs, rowNum) -> new Booking(
                        rs.getLong("id"),
                        rs.getLong("show_seat_id")
                ),
                showSeatId
        );

        return inserted.isEmpty() ? null : inserted.getFirst();
    }
}
