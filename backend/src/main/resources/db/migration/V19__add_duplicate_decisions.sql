-- Decisions of a person on two tenders that may be the same consultation:
-- merged (the duplicate is hidden and refers to the tender kept) or not
-- duplicates. Nothing is deleted: a merge keeps the state of the tender kept
-- before it, the notices and links it moved and the previous kind of the
-- notices it read again (relabeled_kinds), so it can be undone exactly.
-- Rollback (once every merge is undone):
--   DROP TABLE duplicate_decision; ALTER TABLE tender DROP COLUMN merged_into_id;
ALTER TABLE tender ADD COLUMN merged_into_id BIGINT REFERENCES tender (id);

CREATE INDEX idx_tender_merged_into ON tender (merged_into_id) WHERE merged_into_id IS NOT NULL;

CREATE TABLE duplicate_decision (
    id                BIGSERIAL PRIMARY KEY,
    kind              VARCHAR(20)  NOT NULL,
    kept_tender_id    BIGINT       NOT NULL REFERENCES tender (id),
    other_tender_id   BIGINT       NOT NULL REFERENCES tender (id),
    reason            TEXT         NOT NULL,
    decided_by        TEXT         NOT NULL,
    decided_at        TIMESTAMPTZ  NOT NULL,
    kept_before       JSONB,
    moved_notice_ids  BIGINT[],
    moved_link_ids    BIGINT[],
    relabeled_kinds   JSONB,
    undone_by         TEXT,
    undone_at         TIMESTAMPTZ,
    CONSTRAINT ck_duplicate_decision_distinct CHECK (kept_tender_id <> other_tender_id)
);

CREATE INDEX idx_duplicate_decision_tenders ON duplicate_decision (kept_tender_id, other_tender_id);
