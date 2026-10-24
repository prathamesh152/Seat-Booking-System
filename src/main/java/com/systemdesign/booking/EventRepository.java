package com.systemdesign.booking;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countEvents() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events",
                Long.class
        );
    }

    public Event createEvent(
            String idempotencyKey, String name, String description) {

        var inserted = jdbcTemplate.query(
                """
                INSERT INTO events (idempotency_key, name, description)
                VALUES (?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING id, name, description
                """,
                (rs, rowNum) -> new Event(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description")
                ),
                idempotencyKey, name, description
        );

        if (!inserted.isEmpty()) {
            return inserted.getFirst();
        }

        return jdbcTemplate.queryForObject(
                """
                SELECT id, name, description
                FROM events
                WHERE idempotency_key = ?
                """,
                (rs, rowNum) -> new Event(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description")
                ),
                idempotencyKey
        );
    }

    public Event findEventById(long id) {
        return jdbcTemplate.query(
                "SELECT id, name, description FROM events WHERE id = ?",
                (rs, rowNum) -> new Event(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description")
                ),
                id
        ).stream().findFirst().orElse(null);
    }
}