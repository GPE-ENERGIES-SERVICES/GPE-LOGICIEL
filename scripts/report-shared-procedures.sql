-- Read-only report: consultations of the same source that share one eForms
-- procedure identifier (tender.procedure_id, V16). They are probably the same
-- consultation collected twice, but nothing is merged or removed: each case is
-- to be checked and decided by a person. Columns "gpe_*" show the data of the
-- team that a merge would have to keep.
--
-- Diagnosis of each tender:
--   eforms_change: its first notice is an eForms change notice
--     (ChangedNoticeIdentifier) that the BOAMP published with etat = INITIAL;
--   cites_other: that notice cites (annonce_lie) another tender of the group.
-- A tender with both is a rectification read as a new consultation.
--
-- docker compose exec -T postgres psql -U tenders -d tenders --csv \
--   -f - < scripts/report-shared-procedures.sql > procedures-partagees.csv
WITH shared AS (
    SELECT source, procedure_id
    FROM tender
    WHERE procedure_id IS NOT NULL
    GROUP BY source, procedure_id
    HAVING count(*) > 1
), first_notice AS (
    SELECT DISTINCT ON (n.tender_id) n.tender_id, r.payload, r.linked_ids
    FROM tender_notice n
    JOIN raw_notice r ON r.id = n.raw_notice_id
    JOIN tender t ON t.id = n.tender_id AND n.external_id = t.external_id
)
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
       coalesce(f.payload ->> 'donnees', '') LIKE '%ChangedNoticeIdentifier%' AS eforms_change,
       EXISTS (SELECT 1 FROM tender o
               WHERE o.source = t.source AND o.procedure_id = t.procedure_id AND o.id <> t.id
                 AND o.external_id = ANY (f.linked_ids))                    AS cites_other,
       t.follow_up_status                     AS gpe_status,
       t.follow_up_comment IS NOT NULL        AS gpe_comment,
       (SELECT count(*) FROM tender_follow fo WHERE fo.tender_id = t.id) AS gpe_follows,
       (SELECT count(*) FROM dossier d WHERE d.tender_id = t.id)         AS gpe_dossiers
FROM tender t
JOIN shared s ON s.source = t.source AND s.procedure_id = t.procedure_id
LEFT JOIN first_notice f ON f.tender_id = t.id
ORDER BY t.procedure_id, t.published_at, t.id;
