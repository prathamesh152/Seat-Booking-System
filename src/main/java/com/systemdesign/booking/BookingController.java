package com.systemdesign.booking;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
public class BookingController {
    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
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
            Booking created = bookingService.createBooking(
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
            List<Booking> bookings = bookingService.createGroupBooking(
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
}
