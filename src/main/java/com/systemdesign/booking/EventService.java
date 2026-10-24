package com.systemdesign.booking;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
public class EventService {
    private final AtomicLong nextId = new AtomicLong(3);

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event createEvent(
            String idempotencyKey, String name, String description) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not be blank"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Event name must not be blank"
            );
        }

        Event event = eventRepository.createEvent(
                idempotencyKey, name, description
        );

        if (!Objects.equals(event.name(), name)
                || !Objects.equals(event.description(), description)) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key was already used with different event data"
            );
        }

        return event;
    }

    public Event findEventById(long id) {
        return eventRepository.findEventById(id);
    }
}
