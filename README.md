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
The automated test currently checks application-context startup and needs a running, initialized PostgreSQL database:

```powershell
.\mvnw.cmd "-Duser.timezone=UTC" test
```

Lesson experiments performed manually:
- Event creation survives application and database restarts.
- Same event key and input return the same event; changed input returns 409.
- Competing bookings produce one success and conflicts for the remaining attempts.
- Group booking failure rolls back earlier inserts; available groups commit together.

Automated coverage does not yet prove these business scenarios.

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
