BEGIN;

ALTER TABLE assignments ALTER COLUMN title TYPE TEXT;
ALTER TABLE assignments DROP CONSTRAINT IF EXISTS ck_assignments_scenario_length;
ALTER TABLE assignments ADD CONSTRAINT ck_assignments_scenario_length
  CHECK (char_length(title) BETWEEN 1 AND 4000);

-- Earlier records have no reliable creation timestamp. Keep that value unknown.
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE assignments ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE assignments ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

-- Existing open work now follows the assignment deadline instead of an old minute limit.
UPDATE submissions s SET deadline_at = a.due_at
FROM assignments a
WHERE s.assignment_id = a.assignment_id AND s.status = 'IN_PROGRESS'
  AND a.status = 'PUBLISHED' AND a.due_at > s.started_at
  AND a.closed_at IS NULL AND s.deadline_at IS DISTINCT FROM a.due_at;

CREATE TABLE IF NOT EXISTS submission_evaluation_history (
  history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  submission_id BIGINT REFERENCES submissions(submission_id) ON DELETE SET NULL,
  assignment_id BIGINT NOT NULL REFERENCES assignments(assignment_id),
  student_id BIGINT NOT NULL REFERENCES users(user_id),
  actor_id BIGINT REFERENCES users(user_id),
  action VARCHAR(40) NOT NULL,
  reason TEXT NOT NULL DEFAULT '',
  previous_evaluation JSONB NOT NULL,
  next_evaluation JSONB NOT NULL,
  previous_score NUMERIC(10,2),
  next_score NUMERIC(10,2),
  previous_status TEXT,
  next_status TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (jsonb_typeof(previous_evaluation) = 'object'),
  CHECK (jsonb_typeof(next_evaluation) = 'object')
);

CREATE INDEX IF NOT EXISTS ix_evaluation_history_student
  ON submission_evaluation_history(assignment_id, student_id, history_id DESC);

COMMIT;
