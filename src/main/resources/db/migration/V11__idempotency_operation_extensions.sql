-- Step 1: Add missing columns to agent_operations
ALTER TABLE agent_operations ADD COLUMN IF NOT EXISTS operation_id VARCHAR(255);
UPDATE agent_operations SET operation_id = 'op_' || id WHERE operation_id IS NULL;
ALTER TABLE agent_operations ALTER COLUMN operation_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_agent_operations_operation_id ON agent_operations(operation_id);

ALTER TABLE agent_operations ADD COLUMN IF NOT EXISTS resource_type VARCHAR(100);
ALTER TABLE agent_operations ADD COLUMN IF NOT EXISTS resource_id VARCHAR(255);
ALTER TABLE agent_operations ADD COLUMN IF NOT EXISTS request_hash VARCHAR(255);
ALTER TABLE agent_operations ADD COLUMN IF NOT EXISTS result_payload JSONB;
ALTER TABLE agent_operations ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE agent_operations ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255);

-- Step 2: Add response_payload to idempotency_records
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS response_payload JSONB;
