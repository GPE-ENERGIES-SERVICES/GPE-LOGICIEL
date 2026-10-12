-- Read-only report: consultations of the same source that share one eForms
-- procedure identifier (tender.procedure_id). They are probably the same
-- consultation collected twice, but nothing is merged or removed: each case is
-- to be checked and decided by a person. Columns "gpe_*" show the data of the
-- team that a merge would have to keep.
--
-- docker compose exec -T postgres psql -U tenders -d tenders --csv \
--   -f - < scripts/report-shared-procedures.sql > procedures-partagees.csv
SELECT t.procedure_id,
       t.source,
       t.id                                   AS tender_id,
       t.external_id,
       t.title,
       t.buyer,
       t.published_at,
       t.deadline,
       t.status,
       (SELECT string_agg(n.kind || ' ' || n.external_id, ', ' ORDER BY n.published_at, n.id)
        FROM tender_notice n WHERE n.tender_id = t.id) AS notices,
       t.follow_up_status                     AS gpe_status,
       t.follow_up_comment IS NOT NULL        AS gpe_comment,
       (SELECT count(*) FROM tender_follow f WHERE f.tender_id = t.id) AS gpe_follows,
       (SELECT count(*) FROM dossier d WHERE d.tender_id = t.id)      AS gpe_dossiers
FROM tender t
WHERE (t.source, t.procedure_id) IN (SELECT source, procedure_id
                                     FROM tender
                                     WHERE procedure_id IS NOT NULL
                                     GROUP BY source, procedure_id
                                     HAVING count(*) > 1)
ORDER BY t.procedure_id, t.published_at, t.id;
