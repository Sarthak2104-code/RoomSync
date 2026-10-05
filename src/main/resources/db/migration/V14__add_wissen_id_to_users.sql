-- V14__add_wissen_id_to_users.sql
-- Step 1: Add wissen_id column initially nullable
ALTER TABLE users ADD COLUMN wissen_id VARCHAR(50);

-- Step 2: Backfill existing users lacking wissen_id with synthetic POC identifiers (WT900000 + id)
-- Note: Synthetic Wissen ID generated during POC migration.
UPDATE users
SET wissen_id = 'WT' || (900000 + id)::text
WHERE wissen_id IS NULL OR TRIM(wissen_id) = '';

-- Step 3: Enforce NOT NULL constraint
ALTER TABLE users ALTER COLUMN wissen_id SET NOT NULL;

-- Step 4: Create case-insensitive unique index
CREATE UNIQUE INDEX ux_users_wissen_id_lower ON users (LOWER(wissen_id));
