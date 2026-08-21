-- Verify no case-insensitive duplicate room names exist before creating the unique index
DO $$
DECLARE
    duplicate_names TEXT;
BEGIN
    SELECT string_agg(LOWER(name), ', ')
    INTO duplicate_names
    FROM rooms
    GROUP BY LOWER(name)
    HAVING COUNT(*) > 1;

    IF duplicate_names IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot apply migration V5: Duplicate room names found (case-insensitive): %', duplicate_names;
    END IF;
END $$;

-- Drop redundant case-sensitive unique constraint
ALTER TABLE rooms DROP CONSTRAINT IF EXISTS rooms_name_key;

-- Create case-insensitive unique index
CREATE UNIQUE INDEX ux_rooms_name_lower ON rooms (LOWER(name));
