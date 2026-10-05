-- Step 1: Drop existing unique index that enforced uniqueness across all statuses
DROP INDEX IF EXISTS ux_bookings_series_occurrence;

-- Step 2: Re-create unique index enforcing that at most ONE CONFIRMED booking can exist for any (series_id, occurrence_index) pair.
-- Historical CANCELLED bookings may coexist with the CONFIRMED replacement booking.
CREATE UNIQUE INDEX ux_bookings_series_occurrence
ON bookings (series_id, occurrence_index)
WHERE status = 'CONFIRMED'
  AND series_id IS NOT NULL
  AND occurrence_index IS NOT NULL;
