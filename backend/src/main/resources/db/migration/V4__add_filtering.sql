ALTER TABLE tender
    ADD COLUMN description    TEXT,
    ADD COLUMN retained       BOOLEAN,
    ADD COLUMN filter_reason  TEXT,
    ADD COLUMN filtered_at    TIMESTAMPTZ;

CREATE INDEX idx_tender_retained ON tender (retained, deadline);

-- Filtering criteria of the company: a single row, edited by an administrator.
-- Empty lists and NULL values mean "criterion not used".
CREATE TABLE filter_criteria (
    id                        BIGINT PRIMARY KEY,
    departments               TEXT[]         NOT NULL DEFAULT '{}',
    market_types              TEXT[]         NOT NULL DEFAULT '{}',
    min_days_before_deadline  INTEGER,
    cpv_prefixes              TEXT[]         NOT NULL DEFAULT '{}',
    keywords                  TEXT[]         NOT NULL DEFAULT '{}',
    excluded_keywords         TEXT[]         NOT NULL DEFAULT '{}',
    min_amount                NUMERIC(15, 2),
    max_amount                NUMERIC(15, 2),
    updated_at                TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_by                TEXT,
    CONSTRAINT ck_filter_criteria_single_row CHECK (id = 1)
);

INSERT INTO filter_criteria (id) VALUES (1);
