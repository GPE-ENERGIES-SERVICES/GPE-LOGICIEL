-- The review of a filled form: filled by hand or by the AI, then validated or
-- rejected by a user. A validated form is locked and joined to its piece; a
-- rejection says why, and the AI reads it when it fills the form again.
-- Rollback: ALTER TABLE response_form DROP COLUMN status, DROP COLUMN filled_by_ai,
--   DROP COLUMN review_comment, DROP COLUMN reviewed_by, DROP COLUMN reviewed_at;
ALTER TABLE response_form
    ADD COLUMN status          VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('IN_PROGRESS', 'TO_VALIDATE', 'VALIDATED', 'REJECTED')),
    ADD COLUMN filled_by_ai    BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN review_comment  TEXT,
    ADD COLUMN reviewed_by     TEXT,
    ADD COLUMN reviewed_at     TIMESTAMPTZ;
