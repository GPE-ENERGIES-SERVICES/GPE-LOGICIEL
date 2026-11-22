-- How long each call to the AI took: what to expect for the next one of the
-- same kind, shown as the progress of a call; null for the calls made before.
-- Rollback: ALTER TABLE ai_call DROP COLUMN duration_ms;
ALTER TABLE ai_call ADD COLUMN duration_ms INTEGER;
