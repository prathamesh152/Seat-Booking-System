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
- BookingTransactionTests: two tests using real Spring transactions and disposable PostgreSQL 17 containers. They verify group commit and rollback while preserving a pre-existing booking. The schema is loaded from database/schema.sql.
- BookingApplicationTests: original application-context/database-connectivity test against the local lesson database.

Run selected tests without the original local-database test:

```powershell
.\mvnw.cmd '-Duser.timezone=UTC' '-Dtest=BookingRetryCoordinatorTests,BookingTransactionTests' test
```

The integration tests verify actual commit/rollback; the retry unit tests simulate deadlock errors. End-to-end recovery from a real database deadlock in a fresh transaction is not yet tested.