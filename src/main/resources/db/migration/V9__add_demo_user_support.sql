ALTER TABLE users ADD COLUMN expires_at TIMESTAMPTZ;

CREATE INDEX idx_users_expires_at ON users (expires_at) WHERE expires_at IS NOT NULL;
