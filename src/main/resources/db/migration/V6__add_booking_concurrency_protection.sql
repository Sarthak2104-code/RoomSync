-- Step 5: Enable btree_gist extension for GiST index support on scalar types (like BIGINT)
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Verify no existing overlapping CONFIRMED bookings exist for the same room before creating the exclusion constraint
DO $$
DECLARE

    conflict_count INT;
BEGIN
    SELECT COUNT(*)
    INTO conflict_count
    FROM bookings b1
    JOIN bookings b2 ON b1.room_id = b2.room_id
                    AND b1.id < b2.id
                    AND b1.status = 'CONFIRMED'
                    AND b2.status = 'CONFIRMED'
                    AND tstzrange(b1.start_time, b1.end_time, '[)') && tstzrange(b2.start_time, b2.end_time, '[)');

    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'Cannot apply migration V6: Found % overlapping CONFIRMED booking pairs for the same room', conflict_count;
    END IF;
END $$;

-- Add PostgreSQL exclusion constraint guaranteeing no two overlapping CONFIRMED bookings can coexist for the same room
ALTER TABLE bookings
ADD CONSTRAINT no_overlapping_bookings
EXCLUDE USING gist (
    room_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&
)
WHERE (status = 'CONFIRMED');
