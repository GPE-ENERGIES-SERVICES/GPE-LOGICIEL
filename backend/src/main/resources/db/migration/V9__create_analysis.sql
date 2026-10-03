-- One extraction run on a dossier. A new analysis never replaces an earlier
-- one: each keeps the model and the version of the instructions it used.
CREATE TABLE analysis (
    id              BIGSERIAL PRIMARY KEY,
    dossier_id      BIGINT        NOT NULL REFERENCES dossier (id),
    status          VARCHAR(20)   NOT NULL,
    model           TEXT          NOT NULL,
    prompt_version  TEXT          NOT NULL,
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    error_message   TEXT,
    created_at      TIMESTAMPTZ   NOT NULL,
    finished_at     TIMESTAMPTZ
);

CREATE INDEX idx_analysis_dossier ON analysis (dossier_id);

-- One answer of the extraction grid, with the exact quote that justifies it
-- and where it was found.
CREATE TABLE extracted_field (
    id           BIGSERIAL PRIMARY KEY,
    analysis_id  BIGINT        NOT NULL REFERENCES analysis (id),
    field_key    VARCHAR(50)   NOT NULL,
    value        TEXT,
    quote        TEXT,
    document_id  BIGINT        REFERENCES document (id),
    page_number  INTEGER,
    state        VARCHAR(20)   NOT NULL
);

CREATE INDEX idx_extracted_field_analysis ON extracted_field (analysis_id);
