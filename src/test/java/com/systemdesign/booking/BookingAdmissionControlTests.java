package com.systemdesign.booking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BookingAdmissionControlTests {

    @Test
    @Timeout(20)
    void rejectsExcessOperationsAndRecoversAfterRelease()
            throws Exception {

        BookingAdmissionControl admission =
                new BookingAdmissionControl();

        CountDownLatch entered = new CountDownLatch(4);
        CountDownLatch release = new CountDownLatch(1);

        List<Future<String>> accepted = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(4)) {
            try {
                for (int i = 0; i < 4; i++) {
                    accepted.add(executor.submit(
                            () -> admission.execute(() -> {
                                entered.countDown();
                                awaitRelease(release);
                                return "completed";
                            })
                    ));
                }

                // Confirm all four permits are currently occupied.
                assertTrue(
                        entered.await(5, TimeUnit.SECONDS),
                        "Four operations should enter admission control"
                );

                AtomicBoolean rejectedOperationRan =
                        new AtomicBoolean(false);

                assertThrows(
                        BookingOverloadedException.class,
                        () -> admission.execute(() -> {
                            rejectedOperationRan.set(true);
                            return "must not run";
                        })
                );

                assertFalse(rejectedOperationRan.get());

                // Rejection must not accidentally add a permit.
                assertThrows(
                        BookingOverloadedException.class,
                        () -> admission.execute(() -> "must not run")
                );

            } finally {
                // Unblock workers even if an assertion fails.
                release.countDown();
            }

            for (Future<String> result : accepted) {
                assertEquals(
                        "completed",
                        result.get(5, TimeUnit.SECONDS)
                );
            }

            assertEquals(
                    "accepted again",
                    admission.execute(() -> "accepted again")
            );
        }
    }

    @Test
    void failingOperationsDoNotLeakPermits() {
        BookingAdmissionControl admission =
                new BookingAdmissionControl();

        // More failures than the total number of permits.
        for (int i = 0; i < 10; i++) {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> admission.execute(() -> {
                        throw new IllegalStateException(
                                "Simulated booking failure"
                        );
                    })
            );

            assertEquals(
                    "Simulated booking failure",
                    failure.getMessage()
            );
        }

        assertEquals(
                "still available",
                admission.execute(() -> "still available")
        );
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for test cleanup"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Test worker interrupted", exception
            );
        }
    }
}