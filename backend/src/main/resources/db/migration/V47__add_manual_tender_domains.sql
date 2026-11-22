-- The domains of a tender may be chosen by a user (MANUAL): neither the rules
-- nor the AI change them afterwards.
-- Rollback: UPDATE tender SET activities_source = 'RULES' WHERE activities_source = 'MANUAL';
--   then restore the constraint without 'MANUAL'.
ALTER TABLE tender DROP CONSTRAINT ck_tender_activities_source;
ALTER TABLE tender ADD CONSTRAINT ck_tender_activities_source CHECK (activities_source IN ('RULES', 'AI', 'MANUAL'));
