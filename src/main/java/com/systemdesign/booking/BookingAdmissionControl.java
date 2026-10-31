package com.systemdesign.booking;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
public class BookingAdmissionControl {

    private final Semaphore permits = new Semaphore(4);

    public <T> T execute(Supplier<T> operation) {
        if (!permits.tryAcquire()) {
            throw new BookingOverloadedException(
                    "Too many active booking requests; try again later"
            );
        }

        try {
            return operation.get();
        } finally {
            permits.release();
        }
    }
}