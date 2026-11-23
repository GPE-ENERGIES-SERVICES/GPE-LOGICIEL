-- The former memoirs a user chose as models of writing for a memoir: the AI
-- follows their way of writing, never their content. A memoir removed from the
-- library is left out where it is read.
-- Rollback: ALTER TABLE memoir DROP COLUMN style_references;

ALTER TABLE memoir ADD COLUMN style_references BIGINT[] NOT NULL DEFAULT '{}';
