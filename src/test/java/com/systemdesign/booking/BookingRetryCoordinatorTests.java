package com.systemdesign.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingRetryCoordinatorTests {

    private BookingService bookingService;
    private BookingRetryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        coordinator = new BookingRetryCoordinator(bookingService);
    }

    @Test
    void returnsSuccessfulBookingWithoutRetry() {
        Booking expected = new Booking(100, 10);

        when(bookingService.createBooking(2, 10))
                .thenReturn(expected);

        Booking actual = coordinator.createBooking(2, 10);

        assertEquals(expected, actual);
        verify(bookingService, times(1)).createBooking(2, 10);
    }

    @Test
    void retriesAfterDeadlockAndReturnsSuccessfulResult() {
        Booking expected = new Booking(100, 10);

        when(bookingService.createBooking(2, 10))
                .thenThrow(deadlock())
                .thenReturn(expected);

        Booking actual = coordinator.createBooking(2, 10);

        assertEquals(expected, actual);
        verify(bookingService, times(2)).createBooking(2, 10);
    }

    @Test
    void stopsAfterThreeDeadlockedAttempts() {
        CannotAcquireLockException failure = deadlock();

        when(bookingService.createBooking(2, 10))
                .thenThrow(failure);

        BookingRetryExhaustedException exception = assertThrows(
                BookingRetryExhaustedException.class,
                () -> coordinator.createBooking(2, 10)
        );

        assertSame(failure, exception.getCause());
        verify(bookingService, times(3)).createBooking(2, 10);
    }

    @Test
    void doesNotRetryBusinessConflict() {
        var failure = new InsufficientCapacityException(
                "Not enough capacity"
        );

        when(bookingService.createBooking(2, 10))
                .thenThrow(failure);

        InsufficientCapacityException actual = assertThrows(
                InsufficientCapacityException.class,
                () -> coordinator.createBooking(2, 10)
        );

        assertSame(failure, actual);
        verify(bookingService, times(1)).createBooking(2, 10);
    }

    @Test
    void doesNotRetryUnrelatedDatabaseFailure() {
        var failure = new DataAccessResourceFailureException(
                "Connection failure",
                new SQLException("Connection lost", "08006")
        );

        when(bookingService.createBooking(2, 10))
                .thenThrow(failure);

        DataAccessResourceFailureException actual = assertThrows(
                DataAccessResourceFailureException.class,
                () -> coordinator.createBooking(2, 10)
        );

        assertSame(failure, actual);
        verify(bookingService, times(1)).createBooking(2, 10);
    }

    @Test
    void retriesTheWholeGroupWithTheOriginalSeatIds() {
        List<Long> seatIds = List.of(10L, 11L);
        List<Booking> expected = List.of(
                new Booking(100, 10),
                new Booking(101, 11)
        );

        when(bookingService.createGroupBooking(2, seatIds))
                .thenThrow(deadlock())
                .thenReturn(expected);

        List<Booking> actual =
                coordinator.createGroupBooking(2, seatIds);

        assertEquals(expected, actual);

        verify(bookingService, times(2))
                .createGroupBooking(2, seatIds);
    }

    private CannotAcquireLockException deadlock() {
        return new CannotAcquireLockException(
                "Simulated database deadlock",
                new SQLException("deadlock detected", "40P01")
        );
    }
}