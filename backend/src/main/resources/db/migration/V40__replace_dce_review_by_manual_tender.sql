-- A consultation file without tender becomes a tender added by a user (source
-- MANUAL): it then has what any tender has, estimates and memoirs included.
-- Each row of dce_review (V39) becomes one, with its dossiers and analyses;
-- their files stay where they are, the documents keep their path.
-- Rollback: not automatic; the tenders of source MANUAL hold these dossiers.

INSERT INTO tender (source, external_id, title, status, follow_up_status, follow_up_by, follow_up_at, created_at, updated_at)
SELECT 'MANUAL', 'review-' || id, name, 'ACTIVE', 'TO_STUDY', created_by, created_at, created_at, created_at
FROM dce_review;

-- Both at once: a row belongs to a tender or to a review, never to both (V39).
UPDATE dossier d SET tender_id = t.id, review_id = NULL
FROM tender t
WHERE d.review_id IS NOT NULL AND t.source = 'MANUAL' AND t.external_id = 'review-' || d.review_id;

UPDATE analysis a SET tender_id = t.id, review_id = NULL
FROM tender t
WHERE a.review_id IS NOT NULL AND t.source = 'MANUAL' AND t.external_id = 'review-' || a.review_id;

ALTER TABLE dossier DROP COLUMN review_id, ALTER COLUMN tender_id SET NOT NULL;
ALTER TABLE analysis DROP COLUMN review_id, ALTER COLUMN tender_id SET NOT NULL;

DROP TABLE dce_review;
