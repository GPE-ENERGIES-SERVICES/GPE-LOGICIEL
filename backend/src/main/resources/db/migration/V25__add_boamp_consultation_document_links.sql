-- The BOAMP notices of the older formats (MAPA, FNSimple...) give the address
-- of the consultation documents in urlDocConsul, read by the parser since
-- this version only. The notices already collected get it now, as a new
-- reading of each one would give it: one link per tender and address, the
-- address of the latest notice on the tender when it has none, and the
-- eligibility of the tenders concerned evaluated again. Nothing is removed.
-- Rollback: none needed; the links added are ordinary links of their
-- tenders, as a collection records them.

CREATE TEMPORARY TABLE boamp_document_address ON COMMIT DROP AS
WITH found AS (
    SELECT tn.tender_id, tn.published_at, r.id AS raw_notice_id,
           btrim(replace(substring(r.payload ->> 'donnees' FROM '"urlDocConsul": *"([^"]*)"'), '&amp;', '&')) AS value
    FROM tender_notice tn
    JOIN raw_notice r ON r.id = tn.raw_notice_id
    WHERE r.source = 'BOAMP'
      AND r.payload ->> 'donnees' LIKE '%"urlDocConsul"%'
), addresses AS (
    SELECT tender_id, published_at, raw_notice_id,
           CASE WHEN value LIKE 'www.%' THEN 'https://' || value ELSE value END AS url
    FROM found
)
SELECT tender_id, published_at, raw_notice_id, url,
       regexp_replace(lower(substring(url FROM '^https?://(?:[^/?#@]*@)?([^/?#:]+)')), '^www\.', '') AS platform
FROM addresses
-- Only an http(s) address with a host, as the parser and the links keep them
WHERE url ~ '^https?://[^/?#\s]'
  AND url !~ '[\s<>"{}|\\^`]';

INSERT INTO dce_link (tender_id, source, url, platform, status, first_seen_at, last_seen_at)
SELECT DISTINCT ON (tender_id, url) tender_id, 'BOAMP', url, platform, 'AVAILABLE', now(), now()
FROM boamp_document_address
WHERE platform IS NOT NULL AND platform <> ''
ORDER BY tender_id, url
ON CONFLICT (tender_id, url) DO NOTHING;

-- The address of the latest notice, for a BOAMP tender without any
UPDATE tender t
SET platform_url = latest.url
FROM (
    SELECT DISTINCT ON (tender_id) tender_id, url
    FROM boamp_document_address
    ORDER BY tender_id, published_at DESC NULLS LAST, raw_notice_id DESC
) latest
WHERE t.id = latest.tender_id
  AND t.source = 'BOAMP'
  AND t.platform_url IS NULL;

-- The new links follow the eligibility of their tender at the next evaluation.
UPDATE tender_eligibility
SET alert_revision = -1
WHERE tender_id IN (SELECT tender_id FROM boamp_document_address);
