-- The model of a form, dropped by a user when the consultation file gives
-- none in Word (an official DC1, the form of the buyer received apart): it
-- comes before the one found in the file. A form filled from it is then tied
-- to no document of the file.
-- Rollback: ALTER TABLE response_piece DROP COLUMN template_name, DROP COLUMN template_path;
--   (only once no form is left without document_id) ALTER TABLE response_form ALTER COLUMN document_id SET NOT NULL;
ALTER TABLE response_piece
    ADD COLUMN template_name TEXT,
    ADD COLUMN template_path TEXT;

ALTER TABLE response_form ALTER COLUMN document_id DROP NOT NULL;
