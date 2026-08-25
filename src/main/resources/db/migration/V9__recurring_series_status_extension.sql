-- Step 1: Extend booking_series.status check constraint to support PARTIALLY_CONFIRMED
ALTER TABLE booking_series DROP CONSTRAINT IF EXISTS booking_series_status_check;
ALTER TABLE booking_series ADD CONSTRAINT booking_series_status_check 
    CHECK (status IN ('ACTIVE', 'PARTIALLY_CONFIRMED', 'CANCELLED', 'COMPLETED'));
