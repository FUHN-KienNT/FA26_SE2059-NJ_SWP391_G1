BEGIN;

-- A restored draft retains its first publication date; cancellation ends on restore.
ALTER TABLE assignments DROP CONSTRAINT IF EXISTS ck_assignments_draft_timestamps;
ALTER TABLE assignments ADD CONSTRAINT ck_assignments_draft_timestamps
  CHECK (status <> 'DRAFT' OR cancelled_at IS NULL);

CREATE TABLE IF NOT EXISTS submission_content_snapshots (
  submission_id BIGINT PRIMARY KEY REFERENCES submissions(submission_id),
  assignment_data JSONB NOT NULL CHECK (jsonb_typeof(assignment_data) = 'object'),
  questions_data JSONB NOT NULL CHECK (jsonb_typeof(questions_data) = 'array')
);

COMMIT;
