-- What happened to each estimate, kept with it: creation, import, rates
-- copied again, validation, new version. Nothing here is ever changed.
-- Rollback: DROP TABLE estimate_event;
CREATE TABLE estimate_event (
    id           BIGSERIAL       PRIMARY KEY,
    estimate_id  BIGINT          NOT NULL REFERENCES estimate (id) ON DELETE CASCADE,
    kind         VARCHAR(30)     NOT NULL,
    detail       TEXT,
    created_by   TEXT,
    created_at   TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_estimate_event_estimate ON estimate_event (estimate_id, id);
