# Seat Booking System

A Java 25 / Spring Boot learning project built while studying backend engineering and system design.

## Implemented
- Create and retrieve events using PostgreSQL and Spring JDBC.
- Idempotent event creation using an Idempotency-Key header and a unique database constraint.
- Reject conflicting reuse of an event idempotency key with HTTP 409.
- Single-seat booking, show membership validation, and database-enforced prevention of double booking.
- Group booking using Spring transactions: all requested seats are booked or all new bookings roll back.
- Input validation and HTTP 400, 404, and 409 responses.

## Requirements
JDK 25, Docker Desktop with Linux containers, and the included Maven Wrapper.

## Local setup (PowerShell)
Set a local development password in your terminal. Do not commit real credentials:

```powershell
$env:BOOKING_DB_PASSWORD = 'choose-a-local-password'
docker run --name booking-postgres -e POSTGRES_USER=booking -e "POSTGRES_PASSWORD=$env:BOOKING_DB_PASSWORD" -e POSTGRES_DB=booking -p 127.0.0.1:5432:5432 -v booking-pgdata:/var/lib/postgresql/data -d postgres:17
docker exec booking-postgres pg_isready -U booking -d booking
```

Once PostgreSQL accepts connections, initialize a **new, empty database**:

```powershell
Get-Content -Raw .\database\schema.sql | docker exec -i booking-postgres psql -v ON_ERROR_STOP=1 -U booking -d booking
```

The schema file records the tables created during lessons. Do not run it against the existing lesson database; it is not a migration runner. It contains no personal database rows.

Run the backend:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd "-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC" spring-boot:run
```

Adjust JAVA_HOME to your JDK location. In IntelliJ, set BOOKING_DB_PASSWORD in the application's environment variables and -Duser.timezone=UTC in its VM options.

Existing PostgreSQL volumes retain their original password; changing an environment variable does not reset it.

## API
| Method | Path | Purpose |
| --- | --- | --- |
| GET | /welcome | Sample response |
| GET | /hello | Sample response |
| POST | /events | Create event; requires Idempotency-Key header |
| GET | /events/{id} | Retrieve event |
| POST | /shows/{showId}/bookings | Book one show seat |
| POST | /shows/{showId}/group-bookings | Book multiple show seats atomically |

Event body: `{"name":"Java Workshop","description":"Backend engineering"}`

Single booking body: `{"showSeatId":1}`

Group booking body: `{"showSeatIds":[1,2]}`

Shows and show seats currently need to be inserted through SQL. Use IDs from your own database.
Group responses are sorted by seat ID. Booking endpoints do not yet support idempotency.
Cancellation, authentication, payments, and seat holds are not implemented.

## Validation
Run the full automated suite with Docker Desktop running. The original application-context test still needs the local initialized lesson database and BOOKING_DB_PASSWORD; the transaction integration tests create a separate disposable database:

```powershell
.\mvnw.cmd "-Duser.timezone=UTC" test
```

Lesson experiments performed manually:
- Event creation survives application and database restarts.
- Same event key and input return the same event; changed input returns 409.
- Competing bookings produce one success and conflicts for the remaining attempts.
- Group booking failure rolls back earlier inserts; available groups commit together.

Automated transaction tests now cover group commit and rollback. Event idempotency and concurrent capacity checks remain manual scenarios.

## Daily Git workflow
```powershell
Set-Location D:\JAVA\booking
git status
git diff
git add .
git diff --cached
git commit -m "Describe the completed change"
git push
```

Review staged files for secrets and unintended changes before committing. Commit tested learning milestones; write a brief note describing the change and how you verified it.

## Capacity enforcement lesson
Each show has a positive capacity (default 100). Single and group bookings lock the show row with SELECT ... FOR UPDATE inside a Read Committed transaction, count existing bookings, and reject groups that would exceed capacity with HTTP 409. Missing shows return 404. Every capacity-consuming writer must follow this protocol; direct SQL inserts can bypass the application check.

For an existing lesson database that does not yet have capacity, execute database/changes/001_add_show_capacity.sql once in psql after confirming existing booking counts fit the initial limit. Stop the application while applying the change. Do not reapply it or run it after the current fresh-database schema, which already includes capacity.

Manual verification reported during the lesson: capacity 2 accepted two bookings and rejected further requests; two competing requests for different seats with capacity 1 produced one 201, one 409, and one stored booking. The background-job test encourages overlap but does not guarantee simultaneous arrival. These scenarios are not yet covered by automated integration tests.
## Deadlock retries and automated tests
Booking requests pass through a non-transactional retry coordinator to the transactional booking service. Only PostgreSQL deadlocks (SQLSTATE 40P01) are retried, with at most three total attempts and short randomized delays. Business conflicts and unrelated database failures are not retried. Exhaustion returns HTTP 503. Booking endpoints still do not provide idempotency for uncertain network outcomes.

- BookingRetryCoordinatorTests: six unit tests covering normal success, recovery, attempt limits, non-retryable failures, and whole-group retry arguments.
- BookingTransactionTests: three tests using real Spring transactions and disposable PostgreSQL 17 containers. They verify group commit, rollback while preserving a pre-existing booking, and show-lock timeout followed by recovery. The schema is loaded from database/schema.sql.
- BookingApplicationTests: original application-context/database-connectivity test against the local lesson database.

Run selected tests without the original local-database test:

```powershell
.\mvnw.cmd '-Duser.timezone=UTC' '-Dtest=BookingRetryCoordinatorTests,BookingTransactionTests' test
```

The integration tests verify actual commit/rollback; the retry unit tests simulate deadlock errors. End-to-end recovery from a real database deadlock in a fresh transaction is not yet tested.

## Database index review
The current schema already creates indexes for show locking (shows primary key), seat lookup (show_seats primary key), show seat filtering (the leading show_id in UNIQUE (show_id, seat_label)), and booking joins (UNIQUE (show_seat_id)). No redundant indexes were added.

A local plan for show 2 used the composite show-seat index and the unique booking-seat index. One small-data run returned six bookings with 2.359 ms execution time; this is a diagnostic observation, not a load benchmark or production latency target. Exact counts still grow with relevant data and must be reconsidered at scale.

Repeat the read-only check from PowerShell with Docker and the database running:

```powershell
Get-Content -Raw .\database\diagnostics\booking_count_plan.sql | docker exec -i booking-postgres psql -v ON_ERROR_STOP=1 -v show_id=2 -U booking -d booking
```

## Admission control and bounded waiting
BookingAdmissionControl allows at most four active booking operations per application instance using a semaphore. Excess operations are rejected immediately with HTTP 503. A permit spans all deadlock attempts and backoff and is released in finally on success or failure. This is an instructional concurrency limit, not a measured production setting or a requests-per-second rate limit. Multiple application instances each have their own limit, and event endpoints are not covered by it.

Booking transactions set a transaction-local PostgreSQL lock_timeout of two seconds. Timeout while acquiring the show lock (SQLSTATE 55P03) becomes ShowBusyException and HTTP 503 without deadlock retry. The setting ends with the transaction; it is not a total request deadline. The Hikari connection-acquisition timeout is separate from database lock waiting. Normal application pool sizing is unchanged.

BookingAdmissionControlTests uses latches to verify overlapping requests, rejection without executing work, recovery after release, and release after failures. DatabaseConnectionPoolTests uses a separate disposable PostgreSQL container and a two-connection test pool to verify acquisition timeout and subsequent recovery. The application retains its normal pool configuration. HTTP 503 mapping has been checked manually; the new automated checks cover the admission component and database/service behavior.

## Observability
Spring Boot Actuator exposes health and metrics locally at http://127.0.0.1:9091/actuator, while application routes remain on port 8080. Only health and metrics are exposed. Built-in HTTP, JVM, and Hikari metrics are available. HTTP timings can be filtered with repeated tag query parameters, such as uri:/events/{id} and status:200. COUNT and TOTAL_TIME describe completed requests; MAX is windowed, so it can reset while accumulated count and total remain populated. These measurements alone do not provide latency percentiles.

Custom admission metrics:
- booking.operations.active: gauge derived from occupied semaphore permits; includes admitted operations waiting for connections/locks or doing retries/backoff.
- booking.admission.rejected: counter incremented only when a permit cannot be acquired, not when an admitted operation fails.

Both metrics are per instance and reset with process restart. They have no event, seat, or request-ID tags. Admission unit tests verify the active gauge while workers hold permits, rejection counts, and return to zero after completion and failures. Metric endpoint registration was checked manually; management HTTP endpoints are not yet covered by automated endpoint tests. A zero gauge at rest does not prove that no requests occurred between observations.
