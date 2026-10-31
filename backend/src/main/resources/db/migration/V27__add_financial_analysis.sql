-- A second kind of analysis, of the price schedules (DPGF, BPU, DQE), asked
-- for by a user only. Every existing analysis is a summary of the
-- consultation file and stays as it is; nothing is deleted.
-- Rollback: DROP INDEX uk_analysis_unfinished_tender_kind;
--   CREATE UNIQUE INDEX uk_analysis_unfinished_tender ON analysis (tender_id)
--     WHERE status IN ('PENDING', 'PROCESSING', 'RETRY_WAITING');
--   ALTER TABLE analysis DROP COLUMN kind, DROP COLUMN financial;

ALTER TABLE analysis
    ADD COLUMN kind       VARCHAR(20)  NOT NULL DEFAULT 'DCE_SUMMARY',
    ADD COLUMN financial  TEXT;

-- One unfinished analysis per tender and per kind: a summary and an analysis
-- of the prices may wait at the same time.
CREATE UNIQUE INDEX uk_analysis_unfinished_tender_kind ON analysis (tender_id, kind)
    WHERE status IN ('PENDING', 'PROCESSING', 'RETRY_WAITING');

DROP INDEX uk_analysis_unfinished_tender;
