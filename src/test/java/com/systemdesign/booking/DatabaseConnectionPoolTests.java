package com.systemdesign.booking;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.Test;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLTransientConnectionException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class DatabaseConnectionPoolTests {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17")
                    .withDatabaseName("pool_test")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void timesOutWhenExhaustedAndAllowsBorrowingAfterRelease()
            throws Exception {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());

        config.setPoolName("pool-exhaustion-test");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(1000);

        try (HikariDataSource pool = new HikariDataSource(config)) {

            // Borrow every connection the pool permits.
            try (Connection first = pool.getConnection();
                 Connection second = pool.getConnection()) {

                assertFalse(first.isClosed());
                assertFalse(second.isClosed());

                // No connection is returned while this borrower waits.
                SQLTransientConnectionException failure = assertThrows(
                        SQLTransientConnectionException.class,
                        () -> {
                            try (Connection unexpected =
                                         pool.getConnection()) {
                                fail("Pool must not lend a third connection");
                            }
                        }
                );

                System.out.println(
                        "Expected pool timeout: " + failure.getMessage()
                );
            }

            // Closing the borrowed connections returns them to the pool.
            try (Connection availableAgain = pool.getConnection()) {
                assertTrue(availableAgain.isValid(2));
            }
        }
    }
}