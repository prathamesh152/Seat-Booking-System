package com.systemdesign.booking;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.Map;

@RestController
public class BookingController {
    private final BookingRetryCoordinator bookingRetryCoordinator;

    public BookingController(
            BookingRetryCoordinator bookingRetryCoordinator) {
        this.bookingRetryCoordinator = bookingRetryCoordinator;
    }

    @PostMapping("/shows/{showId}/bookings")
    public ResponseEntity<Booking> createBooking(
            @PathVariable("showId") long showId,
            @RequestBody CreateBookingRequest request) {

        if (showId <= 0 || request.showSeatId() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Show ID and show seat ID must be positive"
            );
        }

        try {
            Booking created = bookingRetryCoordinator.createBooking(
                    showId, request.showSeatId()
            );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(created);

        } catch (SeatNotFoundException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage(),
                    exception
            );

        } catch (SeatAlreadyBookedException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }
    }

    @PostMapping("/shows/{showId}/group-bookings")
    public ResponseEntity<List<Booking>> createGroupBooking(
            @PathVariable("showId") long showId,
            @RequestBody CreateGroupBookingRequest request) {

        try {
            List<Booking> bookings = bookingRetryCoordinator.createGroupBooking(
                    showId, request.showSeatIds()
            );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(bookings);

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception
            );

        } catch (SeatNotFoundException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage(),
                    exception
            );

        } catch (SeatAlreadyBookedException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }
    }

    @ExceptionHandler(ShowNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMissingShow(
            ShowNotFoundException exception) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(InsufficientCapacityException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientCapacity(
            InsufficientCapacityException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(BookingRetryExhaustedException.class)
    public ResponseEntity<Map<String, String>> handleRetryExhausted(
            BookingRetryExhaustedException exception) {

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", exception.getMessage()));
    }
}
