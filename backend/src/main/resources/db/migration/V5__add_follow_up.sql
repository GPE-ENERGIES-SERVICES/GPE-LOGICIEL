-- Decision of the company on a tender, shared by all users.
ALTER TABLE tender
    ADD COLUMN follow_up_status   VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    ADD COLUMN follow_up_comment  TEXT,
    ADD COLUMN follow_up_by       TEXT,
    ADD COLUMN follow_up_at       TIMESTAMPTZ;

CREATE INDEX idx_tender_follow_up ON tender (follow_up_status, retained, status);

-- Accent-insensitive search in the list ("electricite" finds "électricité").
-- unaccent is a trusted extension: the database owner can create it.
CREATE EXTENSION IF NOT EXISTS unaccent;
