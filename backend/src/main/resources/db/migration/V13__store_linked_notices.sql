-- Identifiers of the earlier notices a notice refers to, in its own source,
-- as read by the parser of the source. A rectification or an award waiting
-- for its initial notice is applied once that notice is collected, whatever
-- the format of the source.
ALTER TABLE raw_notice ADD COLUMN linked_ids VARCHAR[];

-- BOAMP notices collected before: annonce_lie and annonces_anterieures, read
-- as BoampNoticeParser does (a text, or a list of texts; blank values left out).
UPDATE raw_notice r
SET linked_ids = ARRAY(
    SELECT DISTINCT linked
    FROM (
        SELECT CASE jsonb_typeof(element)
                   WHEN 'string' THEN element #>> '{}'
                   WHEN 'number' THEN element #>> '{}'
                   WHEN 'object' THEN element ->> '#text'
               END AS linked
        FROM jsonb_array_elements(
                 CASE jsonb_typeof(r.payload -> 'annonce_lie')
                     WHEN 'array' THEN r.payload -> 'annonce_lie'
                     WHEN 'string' THEN jsonb_build_array(r.payload -> 'annonce_lie')
                     ELSE CAST('[]' AS jsonb)
                 END
                 || CASE jsonb_typeof(r.payload -> 'annonces_anterieures')
                     WHEN 'array' THEN r.payload -> 'annonces_anterieures'
                     WHEN 'string' THEN jsonb_build_array(r.payload -> 'annonces_anterieures')
                     ELSE CAST('[]' AS jsonb)
                 END) AS element
    ) AS ids
    WHERE linked IS NOT NULL AND btrim(linked) <> '')
WHERE r.source = 'BOAMP';
