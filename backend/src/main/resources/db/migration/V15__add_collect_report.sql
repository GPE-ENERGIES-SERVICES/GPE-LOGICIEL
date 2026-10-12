-- Full report of each collection, until now only written in the logs. Added
-- columns only: the earlier runs keep 0, their report stays in the logs.
-- Rollback: ALTER TABLE collect_run DROP COLUMN ... for each column below.
ALTER TABLE collect_run
    ADD COLUMN notices_known       INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN notices_rejected    INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN tenders_created     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN tenders_updated     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN notices_ignored     INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN notice_errors       INTEGER NOT NULL DEFAULT 0;
