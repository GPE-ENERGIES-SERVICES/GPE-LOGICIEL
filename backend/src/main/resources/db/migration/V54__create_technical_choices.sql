-- The technical choices of GPE for a tender, between its analysis and its
-- estimate: how the energy is used, the roof, the power and its make-up, the
-- constraints and the questions left. Entered by hand, with or without a
-- consultation file; what the file says is only proposed. A change after the
-- validation takes it back.
-- Rollback: DROP TABLE technical_choice;
CREATE TABLE technical_choice (
    tender_id       BIGINT         PRIMARY KEY REFERENCES tender (id) ON DELETE CASCADE,
    valorisation    VARCHAR(20)    CHECK (valorisation IN ('SELF_TOTAL', 'SELF_SURPLUS', 'RESALE_TOTAL')),
    roof            TEXT,
    requested_kwc   NUMERIC(10, 3) CHECK (requested_kwc > 0),
    module_count    INTEGER        CHECK (module_count > 0),
    module_wc       NUMERIC(8, 1)  CHECK (module_wc > 0),
    inverter_count  INTEGER        CHECK (inverter_count > 0),
    inverter_kva    NUMERIC(8, 1)  CHECK (inverter_kva > 0),
    constraints     TEXT,
    open_points     TEXT,
    notes           TEXT,
    updated_by      TEXT,
    updated_at      TIMESTAMPTZ    NOT NULL,
    validated_by    TEXT,
    validated_at    TIMESTAMPTZ
);
