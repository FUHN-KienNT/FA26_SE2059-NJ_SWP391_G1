BEGIN;

ALTER TABLE submissions
  ADD COLUMN IF NOT EXISTS evaluation JSONB NOT NULL DEFAULT '{"format":"ams-evaluation-v1","marks":[]}'::jsonb,
  ADD COLUMN IF NOT EXISTS evaluation_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE submissions DROP CONSTRAINT IF EXISTS ck_submissions_evaluation;
ALTER TABLE submissions ADD CONSTRAINT ck_submissions_evaluation CHECK (
  jsonb_typeof(evaluation) = 'object'
  AND evaluation->>'format' IS NOT DISTINCT FROM 'ams-evaluation-v1'
  AND jsonb_typeof(evaluation->'marks') IS NOT DISTINCT FROM 'array');

ALTER TABLE submissions DROP CONSTRAINT IF EXISTS ck_submissions_evaluation_revision;
ALTER TABLE submissions ADD CONSTRAINT ck_submissions_evaluation_revision CHECK (evaluation_revision >= 0);

COMMIT;
