-- The decision help read with the extraction grid, as checked JSON. The
-- analyses made before stay as they are, without decision: nothing is
-- analysed again.
-- Rollback: ALTER TABLE analysis DROP COLUMN decision;

ALTER TABLE analysis ADD COLUMN decision TEXT;
