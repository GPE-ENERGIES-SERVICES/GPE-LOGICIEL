-- The price schedule of an import is kept, to be opened from the library;
-- null for the imports made before, whose file was not kept.
-- Rollback: ALTER TABLE price_library_import DROP COLUMN storage_path;
ALTER TABLE price_library_import ADD COLUMN storage_path TEXT;
