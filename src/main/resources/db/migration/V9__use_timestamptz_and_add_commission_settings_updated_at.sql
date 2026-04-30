-- Add updated_at to commission_settings; populated from NOW() on each insert
ALTER TABLE commission_settings ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Align existing timestamp columns with the TIMESTAMPTZ convention used in the users table
ALTER TABLE settlement   ALTER COLUMN created_at   TYPE TIMESTAMPTZ USING created_at   AT TIME ZONE 'UTC';
ALTER TABLE purchase     ALTER COLUMN purchased_at TYPE TIMESTAMPTZ USING purchased_at AT TIME ZONE 'UTC';
ALTER TABLE calculation  ALTER COLUMN calculated_at TYPE TIMESTAMPTZ USING calculated_at AT TIME ZONE 'UTC';
