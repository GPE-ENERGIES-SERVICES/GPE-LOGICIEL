-- Whether the consultation file (DCE) of a tender may be retrieved: the tender
-- matches at least one active saved search. Distinct from tender.retained (the
-- criteria of the company), which it never changes. Evaluated again when the
-- tender changes (evaluated_at older than tender.updated_at) or a saved search
-- changes (alert_revision older than the current one).
-- Rollback: DROP TABLE tender_eligibility;
CREATE TABLE tender_eligibility (
    tender_id                BIGINT       PRIMARY KEY REFERENCES tender (id),
    state                    VARCHAR(20)  NOT NULL,
    reason                   TEXT         NOT NULL,
    matched_alert_ids        BIGINT[]     NOT NULL DEFAULT '{}',
    indeterminate_alert_ids  BIGINT[]     NOT NULL DEFAULT '{}',
    alert_revision           BIGINT       NOT NULL,
    evaluated_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_tender_eligibility_state CHECK (state IN ('ELIGIBLE', 'NOT_ELIGIBLE', 'INDETERMINATE'))
);

CREATE INDEX idx_tender_eligibility_state ON tender_eligibility (state);
CREATE INDEX idx_tender_eligibility_matched ON tender_eligibility USING GIN (matched_alert_ids);
