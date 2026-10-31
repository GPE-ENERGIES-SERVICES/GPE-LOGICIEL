-- Words of a page read by optical character recognition (OCR), with their
-- position and confidence, so that the tables of a scanned price schedule
-- can be rebuilt like those of a digital PDF. Null for the other pages.
-- Rollback: ALTER TABLE document_page DROP COLUMN words;

ALTER TABLE document_page ADD COLUMN words TEXT;
