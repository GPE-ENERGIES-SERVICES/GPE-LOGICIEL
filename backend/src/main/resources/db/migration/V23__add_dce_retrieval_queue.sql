-- Queue of the retrieval of the consultation files (DCE), on the links that
-- already exist: every link and its address are kept. A link is only queued
-- when its tender is eligible and its platform is explicitly allowed; none is
-- allowed by default.
-- Rollback: DROP TABLE dce_storage_reservation; DROP TABLE dce_link_event;
-- ALTER TABLE dossier DROP COLUMN stored_bytes;
-- ALTER TABLE dce_link DROP COLUMN attempts, DROP COLUMN next_attempt_at,
--   DROP COLUMN locked_at, DROP COLUMN locked_by, DROP COLUMN fetched_at,
--   DROP COLUMN file_count;
-- UPDATE dce_link SET status = 'AVAILABLE' WHERE length(status) > 20;
-- ALTER TABLE dce_link ALTER COLUMN status TYPE VARCHAR(20);

-- Longer statuses, such as AUTOMATED_ACCESS_NOT_AUTHORIZED.
ALTER TABLE dce_link ALTER COLUMN status TYPE VARCHAR(40);

-- Former statuses: never used in practice (every link is AVAILABLE), mapped
-- to their successor.
UPDATE dce_link SET status = 'ELIGIBLE_PENDING' WHERE status = 'TO_FETCH';
UPDATE dce_link SET status = 'PERMANENT_ERROR' WHERE status = 'FAILED';

ALTER TABLE dce_link
    ADD COLUMN attempts         INT          NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at  TIMESTAMPTZ,
    ADD COLUMN locked_at        TIMESTAMPTZ,
    ADD COLUMN locked_by        VARCHAR(100),
    ADD COLUMN fetched_at       TIMESTAMPTZ,
    ADD COLUMN file_count       INT;

CREATE INDEX idx_dce_link_queue ON dce_link (status, next_attempt_at);

-- Every change of status of a link, with its reason.
CREATE TABLE dce_link_event (
    id           BIGSERIAL    PRIMARY KEY,
    dce_link_id  BIGINT       NOT NULL REFERENCES dce_link (id),
    occurred_at  TIMESTAMPTZ  NOT NULL,
    from_status  VARCHAR(40),
    to_status    VARCHAR(40)  NOT NULL,
    detail       TEXT
);

CREATE INDEX idx_dce_link_event_link ON dce_link_event (dce_link_id, occurred_at);
CREATE INDEX idx_dce_link_event_time ON dce_link_event (occurred_at);

-- Bytes written to the storage directory by a retrieved dossier (received
-- files, archives and extracted files): the quota of the retrieved files is
-- the sum over the retrieved dossiers. Null for the uploaded ones.
ALTER TABLE dossier ADD COLUMN stored_bytes BIGINT;

-- Space reserved by a retrieval in progress, counted in the quota with the
-- files already kept; released at the end, whatever the outcome.
CREATE TABLE dce_storage_reservation (
    dce_link_id  BIGINT       PRIMARY KEY REFERENCES dce_link (id),
    bytes        BIGINT       NOT NULL CHECK (bytes > 0),
    reserved_at  TIMESTAMPTZ  NOT NULL
);
