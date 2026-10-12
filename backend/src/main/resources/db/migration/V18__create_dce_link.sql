-- Where the consultation file (DCE) of a tender can be obtained: an address
-- given by a notice (buyer profile, DCE page, submission page), with the
-- source of that notice. A link is not a file: the files, once obtained (by
-- upload or, later, by retrieval), are a dossier, kept in the storage
-- directory; the database only keeps their metadata.
-- Rollback: ALTER TABLE dossier DROP COLUMN dce_link_id, DROP COLUMN origin;
-- DROP TABLE dce_link;
CREATE TABLE dce_link (
    id             BIGSERIAL PRIMARY KEY,
    tender_id      BIGINT       NOT NULL REFERENCES tender (id),
    source         VARCHAR(50)  NOT NULL,
    url            TEXT         NOT NULL,
    platform       TEXT,
    status         VARCHAR(20)  NOT NULL,
    status_detail  TEXT,
    first_seen_at  TIMESTAMPTZ  NOT NULL,
    last_seen_at   TIMESTAMPTZ  NOT NULL,
    checked_at     TIMESTAMPTZ,
    CONSTRAINT uk_dce_link UNIQUE (tender_id, url)
);

CREATE INDEX idx_dce_link_status ON dce_link (status);

-- How a dossier was obtained: uploaded by a user, or retrieved from a link.
ALTER TABLE dossier
    ADD COLUMN origin       VARCHAR(20) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN dce_link_id  BIGINT REFERENCES dce_link (id);

-- The address known for each tender already collected, from its own source.
INSERT INTO dce_link (tender_id, source, url, platform, status, first_seen_at, last_seen_at)
SELECT id,
       source,
       btrim(platform_url),
       regexp_replace(lower(substring(btrim(platform_url) FROM '^[a-zA-Z]+://([^/:?#]+)')), '^www\.', ''),
       'AVAILABLE',
       created_at,
       updated_at
FROM tender
WHERE btrim(coalesce(platform_url, '')) ~* '^https?://'
ON CONFLICT (tender_id, url) DO NOTHING;
