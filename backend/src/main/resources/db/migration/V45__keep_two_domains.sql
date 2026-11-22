-- GPE works in two domains only: CFO / CFA (code ELECTRICITY) and
-- photovoltaics (code PHOTOVOLTAIC). The other codes are cleared, never the
-- rows: an activity or a library entry keeps its text, without domain.
-- Rollback: none needed, the cleared codes were labels only.
UPDATE company_activity SET activity_code = NULL
WHERE activity_code IS NOT NULL AND activity_code NOT IN ('ELECTRICITY', 'PHOTOVOLTAIC');

UPDATE memoir_library SET activity_code = NULL
WHERE activity_code IS NOT NULL AND activity_code NOT IN ('ELECTRICITY', 'PHOTOVOLTAIC');

UPDATE alert
SET activities = ARRAY(SELECT code FROM unnest(activities) AS code WHERE code IN ('ELECTRICITY', 'PHOTOVOLTAIC'))
WHERE NOT activities <@ ARRAY['ELECTRICITY', 'PHOTOVOLTAIC']::VARCHAR[];
