package com.systemdesign.booking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
public class HelloController {

    private final EventService eventService;

    public HelloController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/welcome")
    public String hello() {
        return "Booking backend is running";
    }

    @GetMapping("/hello")
    public String anotherMethod() {
        return "Hello from the booking app";
    }

    @GetMapping("/events/{id}")
    public Event event(@PathVariable("id") long id) {
        Event found = eventService.findEventById(id);

        if (found == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Event not found"
            );
        }
        return found;
    }

    @PostMapping("/events")
    public ResponseEntity<Event> createEvent(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateEventRequest request) {

        if (idempotencyKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not be blank"
            );
        }

        try {
            Event created = eventService.createEvent(
                    idempotencyKey, request.name(), request.description()
            );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(created);

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception
            );
        }catch (IdempotencyConflictException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }
    }
}