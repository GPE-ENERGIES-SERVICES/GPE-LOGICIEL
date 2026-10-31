-- A dossier whose automatic analysis a user stopped while it was read, and
-- when an analysis was sent to the AI, to tell how long analyses take.
-- Nothing changes for the dossiers and analyses already there.
-- Rollback: ALTER TABLE dossier DROP COLUMN auto_analysis;
--   ALTER TABLE analysis DROP COLUMN started_at;

ALTER TABLE dossier ADD COLUMN auto_analysis BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE analysis ADD COLUMN started_at TIMESTAMPTZ;
