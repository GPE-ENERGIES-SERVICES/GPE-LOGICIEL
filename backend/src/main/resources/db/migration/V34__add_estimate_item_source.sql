-- Where an imported item comes from: the document, the sheet (or table of a
-- PDF) and the row of the price schedule. An item read from a PDF, or from a
-- line the application could not read in full, is to be checked by a user.
-- Rollback: ALTER TABLE estimate_item DROP COLUMN source_document_id,
--   DROP COLUMN source_sheet, DROP COLUMN source_row, DROP COLUMN to_check;
ALTER TABLE estimate_item
    ADD COLUMN source_document_id  BIGINT,
    ADD COLUMN source_sheet        VARCHAR(100),
    ADD COLUMN source_row          INTEGER,
    ADD COLUMN to_check            BOOLEAN NOT NULL DEFAULT FALSE;
