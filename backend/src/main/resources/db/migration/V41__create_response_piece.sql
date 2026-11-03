-- The answer to a tender: each piece the buyer asks, proposed from the analysis
-- of the consultation file or added by a user, with what is joined to it (an
-- official document of the company or a file uploaded) and the check of a user.
-- Rollback: DROP TABLE response_piece; (the files stay under responses/)

CREATE TABLE response_piece (
    id                   BIGSERIAL     PRIMARY KEY,
    tender_id            BIGINT        NOT NULL REFERENCES tender (id),
    category             VARCHAR(20)   NOT NULL,
    label                VARCHAR(500)  NOT NULL,
    -- The label as first read: the same piece read again by a later analysis is not added twice.
    label_key            VARCHAR(500)  NOT NULL,
    from_analysis        BOOLEAN       NOT NULL,
    company_document_id  BIGINT        REFERENCES company_document (id) ON DELETE SET NULL,
    file_name            TEXT,
    storage_path         TEXT,
    size_bytes           BIGINT,
    not_needed           BOOLEAN       NOT NULL DEFAULT FALSE,
    checked_by           TEXT,
    checked_at           TIMESTAMPTZ,
    updated_by           TEXT,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_response_piece_label UNIQUE (tender_id, label_key)
);
