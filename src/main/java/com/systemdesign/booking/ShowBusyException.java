package com.systemdesign.booking;

public class ShowBusyException extends RuntimeException {

    public ShowBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}