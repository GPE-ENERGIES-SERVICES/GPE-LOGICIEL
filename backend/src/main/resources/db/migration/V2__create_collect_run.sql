CREATE TABLE collect_run (
    id                BIGSERIAL PRIMARY KEY,
    source            VARCHAR(50)  NOT NULL,
    trigger           VARCHAR(20)  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    since_date        DATE         NOT NULL,
    started_at        TIMESTAMPTZ  NOT NULL,
    finished_at       TIMESTAMPTZ,
    notices_received  INTEGER      NOT NULL DEFAULT 0,
    notices_new       INTEGER      NOT NULL DEFAULT 0,
    error_message     TEXT
);

CREATE INDEX idx_collect_run_source_status ON collect_run (source, status, started_at DESC);
