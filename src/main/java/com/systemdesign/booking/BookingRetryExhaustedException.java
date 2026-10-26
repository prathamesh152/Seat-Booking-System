package com.systemdesign.booking;

public class BookingRetryExhaustedException extends RuntimeException {

    public BookingRetryExhaustedException(
            String message, Throwable cause) {
        super(message, cause);
    }
}