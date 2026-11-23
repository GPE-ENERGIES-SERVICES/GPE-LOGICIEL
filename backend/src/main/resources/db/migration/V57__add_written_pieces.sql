-- A piece of the answer written in the application, by hand or drafted by the
-- AI: its text, who wrote it, and what the AI could not find. Its Word file is
-- joined to the piece like an uploaded one.
-- Rollback: ALTER TABLE response_piece DROP COLUMN content, DROP COLUMN content_origin, DROP COLUMN content_missing;

ALTER TABLE response_piece
    ADD COLUMN content         TEXT,
    ADD COLUMN content_origin  VARCHAR(10),
    ADD COLUMN content_missing TEXT;
