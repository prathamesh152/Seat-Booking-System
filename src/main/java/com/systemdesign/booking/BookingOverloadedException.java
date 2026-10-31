package com.systemdesign.booking;

public class BookingOverloadedException extends RuntimeException {

    public BookingOverloadedException(String message) {
        super(message);
    }
}