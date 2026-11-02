-- Technical memoirs (« mémoires techniques ») written for the tenders: their
-- requirements read in the consultation file, their outline (a tree of
-- sections), the content of each section, its former versions, the images
-- they hold, the work of the AI, and the library of former memoirs of GPE.
-- Rollback: DROP TABLE memoir_library, memoir_job, memoir_file,
--   memoir_section_revision, memoir_section_requirement, memoir_section,
--   memoir_requirement, memoir;

CREATE TABLE memoir (
    id                    BIGSERIAL       PRIMARY KEY,
    tender_id             BIGINT          REFERENCES tender (id),
    -- The number of the lot, not its id: lots are written again by each rectification.
    lot_number            VARCHAR(50),
    title                 VARCHAR(300)    NOT NULL,
    status                VARCHAR(30)     NOT NULL,
    outline_validated_by  TEXT,
    outline_validated_at  TIMESTAMPTZ,
    created_by            TEXT,
    created_at            TIMESTAMPTZ     NOT NULL,
    updated_by            TEXT,
    updated_at            TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_memoir_tender ON memoir (tender_id);

-- What the consultation file asks of the memoir, with the place it is read.
CREATE TABLE memoir_requirement (
    id           BIGSERIAL       PRIMARY KEY,
    memoir_id    BIGINT          NOT NULL REFERENCES memoir (id) ON DELETE CASCADE,
    position     INTEGER         NOT NULL,
    category     VARCHAR(20)     NOT NULL,
    text         TEXT            NOT NULL,
    weight       VARCHAR(50),
    document_id  BIGINT,
    page         INTEGER,
    quote        TEXT,
    verified     BOOLEAN         NOT NULL DEFAULT FALSE,
    origin       VARCHAR(10)     NOT NULL
);

CREATE INDEX idx_memoir_requirement_memoir ON memoir_requirement (memoir_id, position);

-- A part, sub-part or section of the outline; its content is sanitised HTML.
-- proposed: suggested by the AI, not accepted yet. proposal: a content
-- written by the AI or taken from the company, waiting for the user before
-- it replaces a content already written.
CREATE TABLE memoir_section (
    id              BIGSERIAL       PRIMARY KEY,
    memoir_id       BIGINT          NOT NULL REFERENCES memoir (id) ON DELETE CASCADE,
    parent_id       BIGINT          REFERENCES memoir_section (id) ON DELETE CASCADE,
    position        INTEGER         NOT NULL,
    title           VARCHAR(300)    NOT NULL,
    description     TEXT,
    proposed        BOOLEAN         NOT NULL DEFAULT FALSE,
    content         TEXT,
    content_origin  VARCHAR(10),
    proposal        TEXT,
    proposal_note   TEXT,
    updated_by      TEXT,
    updated_at      TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_memoir_section_memoir ON memoir_section (memoir_id, parent_id, position);

-- Which requirements a section answers: the coverage of the consultation file.
CREATE TABLE memoir_section_requirement (
    section_id      BIGINT  NOT NULL REFERENCES memoir_section (id) ON DELETE CASCADE,
    requirement_id  BIGINT  NOT NULL REFERENCES memoir_requirement (id) ON DELETE CASCADE,
    PRIMARY KEY (section_id, requirement_id)
);

-- A content of a section before it was replaced: nothing written is lost.
CREATE TABLE memoir_section_revision (
    id          BIGSERIAL       PRIMARY KEY,
    section_id  BIGINT          NOT NULL REFERENCES memoir_section (id) ON DELETE CASCADE,
    content     TEXT            NOT NULL,
    origin      VARCHAR(10),
    replaced_by TEXT,
    created_at  TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_memoir_section_revision_section ON memoir_section_revision (section_id, id);

-- An image put in a memoir.
CREATE TABLE memoir_file (
    id            BIGSERIAL       PRIMARY KEY,
    memoir_id     BIGINT          NOT NULL REFERENCES memoir (id) ON DELETE CASCADE,
    file_name     TEXT            NOT NULL,
    storage_path  TEXT            NOT NULL,
    size_bytes    BIGINT          NOT NULL,
    uploaded_by   TEXT,
    uploaded_at   TIMESTAMPTZ     NOT NULL
);

-- A work of the AI on a memoir (analysis of the consultation file, writing
-- of the sections), one at a time, followed and stopped from the interface.
CREATE TABLE memoir_job (
    id                  BIGSERIAL       PRIMARY KEY,
    memoir_id           BIGINT          NOT NULL REFERENCES memoir (id) ON DELETE CASCADE,
    kind                VARCHAR(20)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    current_section_id  BIGINT,
    done_steps          INTEGER         NOT NULL DEFAULT 0,
    total_steps         INTEGER         NOT NULL DEFAULT 0,
    cancel_requested    BOOLEAN         NOT NULL DEFAULT FALSE,
    message             TEXT,
    started_by          TEXT,
    started_at          TIMESTAMPTZ     NOT NULL,
    finished_at         TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_memoir_job_running ON memoir_job (memoir_id) WHERE status = 'RUNNING';

-- A former memoir of GPE, kept as a reference for the style, the structure
-- and the methods: never copied as it is. Its text is read at the import.
CREATE TABLE memoir_library (
    id             BIGSERIAL       PRIMARY KEY,
    title          VARCHAR(300)    NOT NULL,
    written_on     DATE,
    context        TEXT,
    market_type    VARCHAR(100),
    activity_code  VARCHAR(40),
    use_for_ai     BOOLEAN         NOT NULL DEFAULT TRUE,
    file_name      TEXT            NOT NULL,
    storage_path   TEXT            NOT NULL,
    size_bytes     BIGINT          NOT NULL,
    text           TEXT,
    uploaded_by    TEXT,
    uploaded_at    TIMESTAMPTZ     NOT NULL
);
