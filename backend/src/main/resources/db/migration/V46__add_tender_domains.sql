-- The domains of a tender (ELECTRICITY = CFO / CFA, PHOTOVOLTAIC = PV),
-- found by rules on its CPV codes and words at each filtering, or confirmed
-- by the AI analysis of its consultation file (source AI: rules no longer
-- change them). The criteria of the company may require one of them.
-- Every collected tender is filtered again at the next collect, to get them.
-- Rollback: ALTER TABLE tender DROP COLUMN activities, DROP COLUMN activities_source;
--   ALTER TABLE filter_criteria DROP COLUMN activities;
ALTER TABLE tender
    ADD COLUMN activities         VARCHAR[]    NOT NULL DEFAULT '{}',
    ADD COLUMN activities_source  VARCHAR(10),
    ADD CONSTRAINT ck_tender_activities_source CHECK (activities_source IN ('RULES', 'AI'));

CREATE INDEX idx_tender_activities ON tender USING GIN (activities);

ALTER TABLE filter_criteria ADD COLUMN activities VARCHAR[] NOT NULL DEFAULT '{}';

UPDATE tender SET filtered_at = NULL WHERE source <> 'MANUAL';
