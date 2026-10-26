-- Initial schema for a new, empty database. Not an automatic migration.
BEGIN;

CREATE TABLE events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL CHECK (char_length(trim(name)) > 0),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    idempotency_key TEXT UNIQUE
);

CREATE TABLE shows (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    starts_at TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL DEFAULT 100 CHECK (capacity > 0)
);

CREATE TABLE show_seats (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id),
    seat_label TEXT NOT NULL CHECK (char_length(trim(seat_label)) > 0),
    UNIQUE (show_id, seat_label)
);

CREATE TABLE bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    show_seat_id BIGINT NOT NULL UNIQUE REFERENCES show_seats(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMIT;
