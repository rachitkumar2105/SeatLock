ALTER TABLE refresh_tokens
    ADD COLUMN user_agent   VARCHAR(500),
    ADD COLUMN ip_address   VARCHAR(64),
    ADD COLUMN last_used_at TIMESTAMP;

-- Backfill existing rows so last_used_at is never null for tokens issued before this migration.
UPDATE refresh_tokens SET last_used_at = created_at WHERE last_used_at IS NULL;
