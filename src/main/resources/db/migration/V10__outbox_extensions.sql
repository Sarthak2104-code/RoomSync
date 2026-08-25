-- Step 1: Add event_id to notification_outbox
ALTER TABLE notification_outbox ADD COLUMN event_id VARCHAR(255);

-- Populate any existing rows
UPDATE notification_outbox SET event_id = 'evt_' || id WHERE event_id IS NULL;

-- Make event_id NOT NULL and UNIQUE
ALTER TABLE notification_outbox ALTER COLUMN event_id SET NOT NULL;
CREATE UNIQUE INDEX ux_notification_outbox_event_id ON notification_outbox(event_id);

-- Step 2: Extend check constraint for notification_outbox status to allow PROCESSING
ALTER TABLE notification_outbox DROP CONSTRAINT IF EXISTS notification_outbox_status_check;
ALTER TABLE notification_outbox ADD CONSTRAINT chk_notification_outbox_status 
CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'DEAD_LETTER'));
