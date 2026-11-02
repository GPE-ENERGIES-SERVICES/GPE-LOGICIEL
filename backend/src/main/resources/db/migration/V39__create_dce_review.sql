-- A consultation file analysed on its own, without any tender of the database:
-- a name given by the user, then the same dossiers and analyses as a tender.
-- A dossier or an analysis belongs either to a tender or to a review.
-- Rollback (once the rows of the reviews are deleted): DROP INDEX uk_analysis_unfinished_review_kind;
--   ALTER TABLE analysis DROP COLUMN review_id, ALTER COLUMN tender_id SET NOT NULL;
--   ALTER TABLE dossier DROP COLUMN review_id, ALTER COLUMN tender_id SET NOT NULL;
--   DROP TABLE dce_review;

CREATE TABLE dce_review (
    id          BIGSERIAL     PRIMARY KEY,
    name        VARCHAR(300)  NOT NULL,
    created_by  TEXT,
    created_at  TIMESTAMPTZ   NOT NULL
);

ALTER TABLE dossier
    ALTER COLUMN tender_id DROP NOT NULL,
    ADD COLUMN review_id BIGINT REFERENCES dce_review (id),
    ADD CONSTRAINT ck_dossier_owner CHECK ((tender_id IS NULL) <> (review_id IS NULL)),
    ADD CONSTRAINT uk_dossier_review_version UNIQUE (review_id, version);

ALTER TABLE analysis
    ALTER COLUMN tender_id DROP NOT NULL,
    ADD COLUMN review_id BIGINT REFERENCES dce_review (id),
    ADD CONSTRAINT ck_analysis_owner CHECK ((tender_id IS NULL) <> (review_id IS NULL));

CREATE INDEX idx_analysis_review ON analysis (review_id);

-- As for a tender: one unfinished analysis per review and per kind.
CREATE UNIQUE INDEX uk_analysis_unfinished_review_kind ON analysis (review_id, kind)
    WHERE status IN ('PENDING', 'PROCESSING', 'RETRY_WAITING');
