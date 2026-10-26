package com.systemdesign.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Service
public class BookingRetryCoordinator {

    private static final Logger log =
            LoggerFactory.getLogger(BookingRetryCoordinator.class);

    private static final int MAX_ATTEMPTS = 3;

    private final BookingService bookingService;

    public BookingRetryCoordinator(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    public Booking createBooking(long showId, long showSeatId) {
        return executeWithRetry(
                () -> bookingService.createBooking(showId, showSeatId)
        );
    }

    public List<Booking> createGroupBooking(
            long showId, List<Long> showSeatIds) {

        return executeWithRetry(
                () -> bookingService.createGroupBooking(
                        showId, showSeatIds
                )
        );
    }

    private <T> T executeWithRetry(Supplier<T> operation) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operation.get();

            } catch (DataAccessException exception) {
                if (!isDeadlock(exception)) {
                    throw exception;
                }

                if (attempt == MAX_ATTEMPTS) {
                    throw new BookingRetryExhaustedException(
                            "Booking could not complete after repeated deadlocks",
                            exception
                    );
                }

                long delayMillis =
                        ThreadLocalRandom.current().nextLong(25, 76)
                                * attempt;

                log.warn(
                        "Booking deadlock on attempt {}; retrying in {} ms",
                        attempt, delayMillis
                );

                pause(delayMillis);
            }
        }

        throw new IllegalStateException("Unexpected retry loop exit");
    }

    private boolean isDeadlock(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof SQLException sqlException
                    && "40P01".equals(sqlException.getSQLState())) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void pause(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new BookingRetryExhaustedException(
                    "Booking retry was interrupted",
                    exception
            );
        }
    }
}