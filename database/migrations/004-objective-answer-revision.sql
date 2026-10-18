-- Prevent a stale autosave or browser tab from overwriting newer saved selections.
BEGIN;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS answer_revision BIGINT NOT NULL DEFAULT 0 CHECK (answer_revision >= 0);
COMMIT;
