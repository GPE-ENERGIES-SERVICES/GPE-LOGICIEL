-- eForms identifier of the procedure, shared by every notice of a
-- consultation in every source: the exact key used to recognise the same
-- consultation published by two sources. Added column only, no row merged or
-- removed. Rollback: DROP INDEX idx_tender_procedure_id;
-- ALTER TABLE tender DROP COLUMN procedure_id;
ALTER TABLE tender ADD COLUMN procedure_id VARCHAR(100);

CREATE INDEX idx_tender_procedure_id ON tender (procedure_id);

-- Tenders already collected from the BOAMP: the contractfolderid of their
-- notices, as BoampNoticeParser reads it. A tender whose notices give
-- different identifiers keeps none and is reported.
DO $$
DECLARE
    filled INTEGER;
    conflicting INTEGER;
BEGIN
    WITH ids AS (
        SELECT n.tender_id, lower(btrim(r.payload ->> 'contractfolderid')) AS procedure_id
        FROM tender_notice n
        JOIN raw_notice r ON r.id = n.raw_notice_id
        WHERE r.source = 'BOAMP' AND btrim(coalesce(r.payload ->> 'contractfolderid', '')) <> ''
    ), single AS (
        SELECT tender_id, min(procedure_id) AS procedure_id
        FROM ids
        GROUP BY tender_id
        HAVING count(DISTINCT procedure_id) = 1
    )
    UPDATE tender t SET procedure_id = s.procedure_id
    FROM single s
    WHERE t.id = s.tender_id AND t.procedure_id IS NULL;
    GET DIAGNOSTICS filled = ROW_COUNT;

    SELECT count(*) INTO conflicting
    FROM (SELECT n.tender_id
          FROM tender_notice n
          JOIN raw_notice r ON r.id = n.raw_notice_id
          WHERE r.source = 'BOAMP' AND btrim(coalesce(r.payload ->> 'contractfolderid', '')) <> ''
          GROUP BY n.tender_id
          HAVING count(DISTINCT lower(btrim(r.payload ->> 'contractfolderid'))) > 1) c;

    RAISE NOTICE 'Procedure identifier filled on % tenders; % tenders with several identifiers left empty',
        filled, conflicting;
    IF conflicting > 0 THEN
        RAISE WARNING '% tenders have notices giving different procedure identifiers', conflicting;
    END IF;
END $$;
