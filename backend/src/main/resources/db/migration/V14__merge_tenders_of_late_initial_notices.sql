-- A complete rectification received before its initial notice made its own
-- tender; the initial notice, collected afterwards, made a second tender for
-- the same consultation. Each such pair is merged into the tender of the
-- rectification, which cites the initial notice explicitly (linked_ids, same
-- source). Only the information published is merged: the removed tender must
-- hold no data of the team. A pair that does not match exactly is left as it
-- is and reported (WARNING). Runs in the transaction of the migration.
DO $$
DECLARE
    pair RECORD;
    reason TEXT;
    merged INTEGER := 0;
    refused INTEGER := 0;
BEGIN
    FOR pair IN
        SELECT a.id AS kept, a.external_id AS rectification, ar.id AS rectification_raw,
               b.id AS removed, b.external_id AS initial
        FROM tender a
        JOIN tender_notice an ON an.tender_id = a.id AND an.external_id = a.external_id AND an.kind = 'RECTIFICATION'
        JOIN raw_notice ar ON ar.id = an.raw_notice_id AND ar.source = a.source
        JOIN tender b ON b.source = a.source AND b.external_id = ANY (ar.linked_ids) AND b.id <> a.id
        ORDER BY a.id, b.id
    LOOP
        reason := CASE
            WHEN NOT EXISTS (SELECT 1 FROM tender WHERE id = pair.removed)
                THEN 'already merged'
            WHEN NOT EXISTS (SELECT 1 FROM tender_notice
                             WHERE tender_id = pair.removed AND external_id = pair.initial AND kind = 'INITIAL')
                THEN 'the cited tender was not made by its initial notice'
            WHEN (SELECT count(*) FROM tender c JOIN raw_notice r ON r.id = pair.rectification_raw
                  WHERE c.source = r.source AND c.external_id = ANY (r.linked_ids) AND c.id <> pair.kept) > 1
                THEN 'the rectification cites several tenders'
            WHEN (SELECT count(*) FROM tender c
                  JOIN tender_notice cn ON cn.tender_id = c.id AND cn.external_id = c.external_id AND cn.kind = 'RECTIFICATION'
                  JOIN raw_notice cr ON cr.id = cn.raw_notice_id AND cr.source = c.source
                  JOIN tender b ON b.id = pair.removed
                  WHERE c.source = b.source AND b.external_id = ANY (cr.linked_ids)) > 1
                THEN 'the initial notice is cited by several rectifications'
            WHEN (SELECT a.status <> b.status FROM tender a, tender b WHERE a.id = pair.kept AND b.id = pair.removed)
                THEN 'different statuses'
            WHEN EXISTS (SELECT 1 FROM tender WHERE id = pair.removed
                         AND (follow_up_status <> 'NEW' OR follow_up_comment IS NOT NULL
                              OR follow_up_by IS NOT NULL OR follow_up_at IS NOT NULL))
                THEN 'decision of the team on the cited tender'
            WHEN EXISTS (SELECT 1 FROM tender_follow WHERE tender_id = pair.removed)
                THEN 'the cited tender is followed'
            WHEN EXISTS (SELECT 1 FROM dossier WHERE tender_id = pair.removed)
                THEN 'documents on the cited tender'
            WHEN EXISTS (SELECT 1 FROM analysis WHERE tender_id = pair.removed)
                THEN 'analysis on the cited tender'
            WHEN EXISTS (SELECT 1 FROM lot WHERE tender_id = pair.kept)
                 AND EXISTS (SELECT number FROM lot WHERE tender_id = pair.removed
                             EXCEPT SELECT number FROM lot WHERE tender_id = pair.kept)
                THEN 'lots of the initial notice missing from the rectification'
        END;

        IF reason IS NOT NULL THEN
            IF reason <> 'already merged' THEN
                RAISE WARNING 'Tenders % (%) and % (%) not merged: %',
                    pair.kept, pair.rectification, pair.removed, pair.initial, reason;
                refused := refused + 1;
            END IF;
            CONTINUE;
        END IF;

        -- The notices of the initial tender join the tender of the rectification.
        UPDATE tender_notice SET tender_id = pair.kept WHERE tender_id = pair.removed;
        -- The lots of the rectification prevail; the lots of the initial notice are kept only when it has none.
        IF EXISTS (SELECT 1 FROM lot WHERE tender_id = pair.kept) THEN
            DELETE FROM lot WHERE tender_id = pair.removed;
        ELSE
            UPDATE lot SET tender_id = pair.kept WHERE tender_id = pair.removed;
        END IF;
        -- What the rectification does not say is taken from the initial notice; the consultation
        -- was published with its initial notice.
        UPDATE tender a SET
            description      = COALESCE(a.description, b.description),
            buyer            = COALESCE(a.buyer, b.buyer),
            reference        = COALESCE(a.reference, b.reference),
            location         = COALESCE(a.location, b.location),
            departments      = CASE WHEN cardinality(a.departments) = 0 THEN b.departments ELSE a.departments END,
            market_types     = CASE WHEN cardinality(a.market_types) = 0 THEN b.market_types ELSE a.market_types END,
            procedure_type   = COALESCE(a.procedure_type, b.procedure_type),
            cpv_codes        = CASE WHEN cardinality(a.cpv_codes) = 0 THEN b.cpv_codes ELSE a.cpv_codes END,
            estimated_amount = COALESCE(a.estimated_amount, b.estimated_amount),
            published_at     = LEAST(a.published_at, b.published_at),
            deadline         = COALESCE(a.deadline, b.deadline),
            notice_url       = COALESCE(a.notice_url, b.notice_url),
            platform_url     = COALESCE(a.platform_url, b.platform_url),
            updated_at       = now()
        FROM tender b
        WHERE a.id = pair.kept AND b.id = pair.removed;
        DELETE FROM tender WHERE id = pair.removed;

        RAISE NOTICE 'Tender % (%) merged into tender % (%)', pair.removed, pair.initial, pair.kept, pair.rectification;
        merged := merged + 1;
    END LOOP;
    RAISE NOTICE 'Tenders of late initial notices: % merged, % not merged', merged, refused;
END $$;
