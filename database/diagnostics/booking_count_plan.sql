-- Read-only diagnostics. Usage: psql -v show_id=2 -f this_file.sql
\if :{?show_id}
\else
\set show_id 2
\endif

SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('bookings', 'show_seats', 'shows')
ORDER BY tablename, indexname;

EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
FROM bookings b
JOIN show_seats s ON s.id = b.show_seat_id
WHERE s.show_id = :show_id;
