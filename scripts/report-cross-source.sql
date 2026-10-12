-- Read-only report of the consultations published by several sources that a
-- person must decide on. Nothing is merged automatically.
--
-- docker compose exec -T postgres psql -U tenders -d tenders --csv \
--   -f - < scripts/report-cross-source.sql > rapprochements.csv
--
-- 1. Proposed matches: a consultation created by a source (BOAMP) when the
--    same eForms procedure already had a tender of another source (TED).
--    Columns "gpe_*" show the data of the team on each side.
-- 2. TED notices set aside: their procedure is the one of several tenders of
--    other sources (see scripts/report-shared-procedures.sql for the cause).
SELECT 'match proposed'                     AS kind,
       m.id                                 AS match_id,
       m.created_at,
       t.procedure_id,
       t.id || ' ' || t.source || ' ' || t.external_id                   AS tender,
       o.id || ' ' || o.source || ' ' || o.external_id                   AS other_tender,
       t.title,
       t.follow_up_status || ' / ' || o.follow_up_status                 AS gpe_status,
       (t.follow_up_comment IS NOT NULL) OR (o.follow_up_comment IS NOT NULL) AS gpe_comment,
       (SELECT count(*) FROM tender_follow f WHERE f.tender_id IN (t.id, o.id)) AS gpe_follows,
       (SELECT count(*) FROM dossier d WHERE d.tender_id IN (t.id, o.id))      AS gpe_dossiers
FROM tender_match m
JOIN tender t ON t.id = m.tender_id
JOIN tender o ON o.id = m.matched_tender_id
WHERE m.status = 'PROPOSED'
UNION ALL
SELECT 'notice set aside',
       NULL,
       r.collected_at,
       lower(btrim(r.payload ->> 'procedure-identifier')),
       r.source || ' ' || r.external_id,
       (SELECT string_agg(t.id || ' ' || t.source || ' ' || t.external_id, ', ' ORDER BY t.id)
        FROM tender t
        WHERE t.procedure_id = lower(btrim(r.payload ->> 'procedure-identifier')) AND t.source <> r.source),
       r.payload -> 'title-proc' ->> 'fra',
       NULL, NULL, NULL, NULL
FROM raw_notice r
WHERE r.normalization_note = 'Procedure of several tenders of other sources'
ORDER BY 1, 3;
