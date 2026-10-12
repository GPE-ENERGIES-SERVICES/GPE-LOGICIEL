-- Source of each notice in the history of a tender. A notice of another
-- source (TED publishing a BOAMP consultation) is kept in the history of the
-- tender with its own source, and never changes the tender.
-- Rollback: ALTER TABLE tender_notice DROP COLUMN source; DROP TABLE tender_match;
ALTER TABLE tender_notice ADD COLUMN source VARCHAR(50);

UPDATE tender_notice n SET source = r.source
FROM raw_notice r
WHERE r.id = n.raw_notice_id;

ALTER TABLE tender_notice ALTER COLUMN source SET NOT NULL;

-- Two tenders of different sources with the same eForms procedure
-- identifier, found when the later one was created: a proposal that a person
-- confirms or rejects. Nothing is merged automatically.
CREATE TABLE tender_match (
    id                 BIGSERIAL PRIMARY KEY,
    tender_id          BIGINT       NOT NULL REFERENCES tender (id),
    matched_tender_id  BIGINT       NOT NULL REFERENCES tender (id),
    rule               VARCHAR(30)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    decided_by         TEXT,
    decided_at         TIMESTAMPTZ,
    CONSTRAINT uk_tender_match UNIQUE (tender_id, matched_tender_id),
    CONSTRAINT ck_tender_match_distinct CHECK (tender_id <> matched_tender_id)
);

CREATE INDEX idx_tender_match_matched ON tender_match (matched_tender_id);
