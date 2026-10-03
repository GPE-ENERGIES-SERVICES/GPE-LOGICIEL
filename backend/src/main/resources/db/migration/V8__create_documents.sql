-- A consultation file (DCE) uploaded for a tender. A new upload creates a
-- new version; earlier versions are kept.
CREATE TABLE dossier (
    id            BIGSERIAL PRIMARY KEY,
    tender_id     BIGINT        NOT NULL REFERENCES tender (id),
    version       INTEGER       NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    error_message TEXT,
    uploaded_by   TEXT,
    uploaded_at   TIMESTAMPTZ   NOT NULL,
    processed_at  TIMESTAMPTZ,
    CONSTRAINT uk_dossier_tender_version UNIQUE (tender_id, version)
);

-- One file of a dossier. Files themselves live in the storage directory;
-- the database keeps their reference and the extracted text.
CREATE TABLE document (
    id                    BIGSERIAL PRIMARY KEY,
    dossier_id            BIGINT        NOT NULL REFERENCES dossier (id),
    file_name             TEXT          NOT NULL,
    storage_path          TEXT          NOT NULL,
    pdf_path              TEXT,
    size_bytes            BIGINT        NOT NULL,
    sha256                VARCHAR(64)   NOT NULL,
    piece_type            VARCHAR(20)   NOT NULL,
    piece_type_corrected  BOOLEAN       NOT NULL DEFAULT FALSE,
    state                 VARCHAR(20)   NOT NULL,
    state_detail          TEXT,
    page_count            INTEGER
);

CREATE INDEX idx_document_dossier ON document (dossier_id);
CREATE INDEX idx_document_sha256 ON document (sha256);

CREATE TABLE document_page (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT        NOT NULL REFERENCES document (id),
    page_number  INTEGER       NOT NULL,
    text         TEXT          NOT NULL,
    method       VARCHAR(20)   NOT NULL,
    CONSTRAINT uk_document_page UNIQUE (document_id, page_number)
);
