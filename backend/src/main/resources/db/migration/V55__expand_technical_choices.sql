-- The technical choices for every tender, PV or CFO/CFA: what the
-- consultation file requires and how GPE answers it, the questions to the
-- buyer, the key quantities, the roof planes (PV), the site, safety and
-- planning, the production. The modules and inverters go to the equipment
-- chosen, with their quantity and unit power, so that several models of
-- inverter can be chosen. What the AI read in the file is marked with its
-- source; a user always has the last word.
-- Rollback: DROP TABLE technical_line;
--   ALTER TABLE tender_equipment DROP COLUMN quantity, DROP COLUMN unit_power, DROP COLUMN note;
--   ALTER TABLE technical_choice DROP COLUMN site_access, DROP COLUMN safety, DROP COLUMN subcontracting,
--     DROP COLUMN planning, DROP COLUMN yield_kwh_kwc, DROP COLUMN production_mwh, DROP COLUMN ai_sources,
--     DROP COLUMN prefilled_at, ADD COLUMN module_count INTEGER, ADD COLUMN module_wc NUMERIC(8, 1),
--     ADD COLUMN inverter_count INTEGER, ADD COLUMN inverter_kva NUMERIC(8, 1), ADD COLUMN open_points TEXT;

ALTER TABLE tender_equipment
    ADD COLUMN quantity    NUMERIC(12, 2) CHECK (quantity > 0),
    ADD COLUMN unit_power  NUMERIC(10, 2) CHECK (unit_power > 0),
    ADD COLUMN note        TEXT;

-- The modules and inverters entered here before go to the equipment of the same kind, when there is one.
UPDATE tender_equipment e SET quantity = c.module_count, unit_power = c.module_wc
FROM technical_choice c, equipment_sheet s
WHERE c.tender_id = e.tender_id AND s.id = e.sheet_id AND s.category = 'MODULE';

ALTER TABLE technical_choice
    DROP COLUMN module_count,
    DROP COLUMN module_wc,
    DROP COLUMN inverter_count,
    DROP COLUMN inverter_kva,
    ADD COLUMN site_access     TEXT,
    ADD COLUMN safety          TEXT,
    ADD COLUMN subcontracting  TEXT,
    ADD COLUMN planning        TEXT,
    ADD COLUMN yield_kwh_kwc   NUMERIC(8, 1)  CHECK (yield_kwh_kwc > 0),
    ADD COLUMN production_mwh  NUMERIC(12, 2) CHECK (production_mwh > 0),
    -- For each value proposed by the AI and not changed since: its document, page and quote.
    ADD COLUMN ai_sources      JSONB          NOT NULL DEFAULT '{}',
    ADD COLUMN prefilled_at    TIMESTAMPTZ;

-- REQUIREMENT: what the file requires, GPE's answer and its status (TO_CHECK, COMPLIANT, NOT_COMPLIANT, VARIANT).
-- QUESTION: to ask the buyer (TO_ASK, ASKED, ANSWERED), the answer.
-- QUANTITY: a key quantity, its value and unit.
-- PLANE: a roof plane (PV): its modules, orientation and tilt.
CREATE TABLE technical_line (
    id            BIGSERIAL      PRIMARY KEY,
    tender_id     BIGINT         NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    kind          VARCHAR(20)    NOT NULL CHECK (kind IN ('REQUIREMENT', 'QUESTION', 'QUANTITY', 'PLANE')),
    position      INTEGER        NOT NULL,
    text          TEXT           NOT NULL,
    answer        TEXT,
    status        VARCHAR(20)    CHECK (status IN ('TO_CHECK', 'COMPLIANT', 'NOT_COMPLIANT', 'VARIANT', 'TO_ASK',
                                     'ASKED', 'ANSWERED')),
    value         NUMERIC(14, 3),
    unit          VARCHAR(20),
    module_count  INTEGER        CHECK (module_count > 0),
    orientation   VARCHAR(60),
    tilt          NUMERIC(5, 1),
    origin        VARCHAR(10)    NOT NULL CHECK (origin IN ('AI', 'USER')),
    document_id   BIGINT         REFERENCES document (id) ON DELETE SET NULL,
    page          INTEGER,
    quote         TEXT,
    updated_by    TEXT,
    updated_at    TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_technical_line_tender ON technical_line (tender_id, kind, position);

-- The open points written as text before become questions to the buyer, one per line.
INSERT INTO technical_line (tender_id, kind, position, text, status, origin, updated_by, updated_at)
SELECT c.tender_id, 'QUESTION', point.n, btrim(point.text), 'TO_ASK', 'USER', c.updated_by, c.updated_at
FROM technical_choice c,
     LATERAL regexp_split_to_table(c.open_points, E'\n') WITH ORDINALITY AS point(text, n)
WHERE c.open_points IS NOT NULL AND btrim(point.text) <> '';

ALTER TABLE technical_choice DROP COLUMN open_points;
