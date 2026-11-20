package com.systemdesign.booking;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
public class BookingAdmissionControl {

    private static final int MAX_ACTIVE_OPERATIONS = 4;

    private final Semaphore permits =
            new Semaphore(MAX_ACTIVE_OPERATIONS);

    private final Counter rejected;

    public BookingAdmissionControl(MeterRegistry registry) {
        rejected = Counter.builder("booking.admission.rejected")
                .description("Booking operations rejected at admission")
                .register(registry);

        Gauge.builder(
                        "booking.operations.active",
                        permits,
                        semaphore ->
                                MAX_ACTIVE_OPERATIONS
                                        - semaphore.availablePermits()
                )
                .description("Currently admitted booking operations")
                .register(registry);
    }

    public <T> T execute(Supplier<T> operation) {
        if (!permits.tryAcquire()) {
            rejected.increment();

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