-- Choice of a user on sending a document to the AI (INCLUDE or EXCLUDE),
-- which prevails over the rules; null, the rules decide. Nothing changes for
-- the documents already there.
-- Rollback: ALTER TABLE document DROP COLUMN ai_choice;

ALTER TABLE document ADD COLUMN ai_choice VARCHAR(10);
