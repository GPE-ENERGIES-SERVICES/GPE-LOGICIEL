-- The result of an answer: the award of a contract won (or lost, to keep who
-- won it), one per lot at most, and the history of the follow-up statuses.
-- New statuses AWARDED, SIGNED and REJECTED need no change: the column is free
-- text. Nothing is deleted or rewritten.
-- Rollback: DROP TABLE tender_award, tender_status_change;
--   (price_library_import) restore the former check on ELECTRICITY, PHOTOVOLTAIC once no IRVE row is left.

-- One award per tender and lot (a single contract has no lot): recording it
-- again, or reading it again from a notice, updates the same row.
CREATE TABLE tender_award (
    id          BIGSERIAL     PRIMARY KEY,
    tender_id   BIGINT        NOT NULL REFERENCES tender (id),
    lot_number  VARCHAR(50),
    winner      VARCHAR(300)  NOT NULL,
    amount      NUMERIC(15, 2) CHECK (amount >= 0),
    awarded_at  DATE          NOT NULL,
    -- NOTICE: read in the published award notice; MANUAL: confirmed by a user.
    source      VARCHAR(10)   NOT NULL CHECK (source IN ('NOTICE', 'MANUAL')),
    -- Won by the company, or by another one (offer rejected).
    won         BOOLEAN       NOT NULL,
    recorded_by VARCHAR(200),
    recorded_at TIMESTAMPTZ   NOT NULL
);

CREATE UNIQUE INDEX uq_tender_award_lot ON tender_award (tender_id, COALESCE(lot_number, ''));

-- Every change of the follow-up status, never updated.
CREATE TABLE tender_status_change (
    id          BIGSERIAL     PRIMARY KEY,
    tender_id   BIGINT        NOT NULL REFERENCES tender (id),
    old_status  VARCHAR(20)   NOT NULL,
    new_status  VARCHAR(20)   NOT NULL,
    -- USER: chosen by a user; NOTICE: from a published award notice confirmed by a user.
    origin      VARCHAR(10)   NOT NULL CHECK (origin IN ('USER', 'NOTICE')),
    changed_by  VARCHAR(200),
    reason      TEXT,
    changed_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_tender_status_change_tender ON tender_status_change (tender_id, changed_at);

-- IRVE (charging points for electric vehicles) becomes a domain of its own.
-- It was found under CFO / CFA so far: whoever asked for CFO / CFA keeps
-- receiving those tenders.
ALTER TABLE price_library_import DROP CONSTRAINT IF EXISTS price_library_import_domain_check;
ALTER TABLE price_library_import ADD CONSTRAINT price_library_import_domain_check
    CHECK (domain IN ('ELECTRICITY', 'PHOTOVOLTAIC', 'IRVE'));

UPDATE filter_criteria SET activities = array_append(activities, 'IRVE')
WHERE 'ELECTRICITY' = ANY (activities) AND NOT 'IRVE' = ANY (activities);

UPDATE alert SET activities = array_append(activities, 'IRVE')
WHERE 'ELECTRICITY' = ANY (activities) AND NOT 'IRVE' = ANY (activities);
