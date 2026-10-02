-- Common format, independent of the source. Missing information stays NULL
-- (displayed "Non indiqué"), never replaced by a default value.
CREATE TABLE tender (
    id                BIGSERIAL PRIMARY KEY,
    source            VARCHAR(50)   NOT NULL,
    external_id       VARCHAR(100)  NOT NULL,
    title             TEXT          NOT NULL,
    buyer             TEXT,
    reference         TEXT,
    location          TEXT,
    departments       TEXT[]        NOT NULL DEFAULT '{}',
    market_types      TEXT[]        NOT NULL DEFAULT '{}',
    procedure_type    VARCHAR(30),
    cpv_codes         TEXT[]        NOT NULL DEFAULT '{}',
    estimated_amount  NUMERIC(15, 2),
    published_at      DATE,
    deadline          TIMESTAMPTZ,
    notice_url        TEXT,
    platform_url      TEXT,
    status            VARCHAR(20)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_tender_source_external_id UNIQUE (source, external_id)
);

CREATE INDEX idx_tender_deadline ON tender (deadline);

CREATE TABLE lot (
    id                BIGSERIAL PRIMARY KEY,
    tender_id         BIGINT        NOT NULL REFERENCES tender (id),
    position          INTEGER       NOT NULL,
    number            VARCHAR(50)   NOT NULL,
    title             TEXT,
    estimated_amount  NUMERIC(15, 2)
);

CREATE INDEX idx_lot_tender ON lot (tender_id);

-- Every publication attached to a tender: initial notice, rectifications,
-- award and cancellation. Keeps the deadline each one announced.
CREATE TABLE tender_notice (
    id             BIGSERIAL PRIMARY KEY,
    tender_id      BIGINT        NOT NULL REFERENCES tender (id),
    raw_notice_id  BIGINT        NOT NULL REFERENCES raw_notice (id),
    external_id    VARCHAR(100)  NOT NULL,
    kind           VARCHAR(20)   NOT NULL,
    published_at   DATE,
    deadline       TIMESTAMPTZ,
    details        TEXT,
    CONSTRAINT uk_tender_notice_raw_notice UNIQUE (raw_notice_id)
);

CREATE INDEX idx_tender_notice_tender ON tender_notice (tender_id);
CREATE INDEX idx_tender_notice_external_id ON tender_notice (external_id);

ALTER TABLE raw_notice
    ADD COLUMN normalized_at       TIMESTAMPTZ,
    ADD COLUMN normalization_note  TEXT;

CREATE INDEX idx_raw_notice_pending ON raw_notice (id) WHERE normalized_at IS NULL;
