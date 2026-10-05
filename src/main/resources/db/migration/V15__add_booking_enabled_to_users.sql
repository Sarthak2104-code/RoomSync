-- V15__add_booking_enabled_to_users.sql
-- Adds booking_enabled boolean column to users table with DEFAULT TRUE

ALTER TABLE users ADD COLUMN booking_enabled BOOLEAN NOT NULL DEFAULT TRUE;
