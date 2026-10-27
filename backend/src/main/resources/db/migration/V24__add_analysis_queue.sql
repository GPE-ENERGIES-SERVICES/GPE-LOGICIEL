-- Persistent queue of the AI analyses, on the analysis table itself: each
-- row stays the history of one request. Nothing is ever sent to a provider
-- while analysis.enabled is false (the default); no row is created then.
-- Rollback: DROP INDEX uk_analysis_unfinished_tender; DROP INDEX idx_analysis_queue;
-- UPDATE analysis SET status = 'FAILED' WHERE status IN ('RETRY_WAITING', 'CANCELLED');
-- ALTER TABLE analysis DROP COLUMN provider, DROP COLUMN requested_by,
--   DROP COLUMN error_kind, DROP COLUMN attempts, DROP COLUMN next_attempt_at,
--   DROP COLUMN locked_at, DROP COLUMN locked_by;

ALTER TABLE analysis
    ADD COLUMN provider         VARCHAR(50),
    ADD COLUMN requested_by     TEXT,
    ADD COLUMN error_kind       VARCHAR(30),
    ADD COLUMN attempts         INT          NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at  TIMESTAMPTZ,
    ADD COLUMN locked_at        TIMESTAMPTZ,
    ADD COLUMN locked_by        VARCHAR(100);

-- Analyses already finished were made in one attempt.
UPDATE analysis SET attempts = 1 WHERE status IN ('DONE', 'FAILED');

-- An unfinished analysis left by an older version waits for the worker.
UPDATE analysis SET next_attempt_at = created_at WHERE status IN ('PENDING', 'PROCESSING');

-- At most one unfinished analysis per tender, even with two requests at the
-- same time: the older duplicates, if any, are closed first.
UPDATE analysis a SET status = 'FAILED', error_kind = 'UNEXPECTED', finished_at = now(),
       error_message = 'Demande en double, close à la mise en place de la file d''analyse'
WHERE a.status IN ('PENDING', 'PROCESSING')
  AND EXISTS (SELECT 1 FROM analysis b WHERE b.tender_id = a.tender_id
              AND b.status IN ('PENDING', 'PROCESSING') AND b.id > a.id);

CREATE UNIQUE INDEX uk_analysis_unfinished_tender ON analysis (tender_id)
    WHERE status IN ('PENDING', 'PROCESSING', 'RETRY_WAITING');

CREATE INDEX idx_analysis_queue ON analysis (status, next_attempt_at);
