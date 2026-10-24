package com.systemdesign.booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public Booking createBooking(long showId, long showSeatId) {
        return createGroupBooking(
                showId, List.of(showSeatId)
        ).getFirst();
    }

    @Transactional
    public List<Booking> createGroupBooking(
            long showId, List<Long> showSeatIds) {

        validateRequest(showId, showSeatIds);

        // Consistent insertion order reduces deadlock risk.
        List<Long> orderedSeatIds = showSeatIds.stream()
                .sorted()
                .toList();

        for (long seatId : orderedSeatIds) {
            if (!bookingRepository.seatBelongsToShow(seatId, showId)) {
                throw new SeatNotFoundException(
                        "Seat " + seatId + " not found for show " + showId
                );
            }
        }

        List<Booking> bookings = new ArrayList<>();

        for (long seatId : orderedSeatIds) {
            Booking booking = bookingRepository.createBooking(seatId);

            if (booking == null) {
                throw new SeatAlreadyBookedException(
                        "Seat " + seatId + " is already booked"
                );
            }

            bookings.add(booking);
        }

        return List.copyOf(bookings);
    }

    private void validateRequest(long showId, List<Long> showSeatIds) {
        if (showId <= 0) {
            throw new IllegalArgumentException(
                    "Show ID must be positive"
            );
        }

        if (showSeatIds == null || showSeatIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one seat ID is required"
            );
        }

        for (Long seatId : showSeatIds) {
            if (seatId == null || seatId <= 0) {
                throw new IllegalArgumentException(
                        "Seat IDs must be non-null and positive"
                );
            }
        }

        if (new HashSet<>(showSeatIds).size() != showSeatIds.size()) {
            throw new IllegalArgumentException(
                    "Repeated seat IDs are not allowed"
            );
        }
    }
}