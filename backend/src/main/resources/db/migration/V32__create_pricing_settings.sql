-- Settings of GPE for its estimates, edited by an administrator: VAT rate
-- and sale coefficient of each type of cost given to a new estimate, and the
-- rates (cost of an hour of each profile, of a kilometre, of a day of
-- rental...) picked in an estimate. An estimate keeps its own copy of all of
-- them: a change here never changes an estimate already made.
-- Rollback: ALTER TABLE estimate_component DROP COLUMN rate_code;
--   DROP TABLE pricing_rate; DROP TABLE pricing_coefficient; DROP TABLE pricing_settings;
CREATE TABLE pricing_settings (
    id          SMALLINT        PRIMARY KEY,
    vat_rate    NUMERIC(6, 4)   NOT NULL DEFAULT 0.2000,
    updated_by  TEXT,
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_pricing_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_pricing_settings_vat_rate CHECK (vat_rate >= 0 AND vat_rate < 1)
);

INSERT INTO pricing_settings (id) VALUES (1);

CREATE TABLE pricing_coefficient (
    settings_id     SMALLINT        NOT NULL REFERENCES pricing_settings (id),
    component_type  VARCHAR(20)     NOT NULL,
    coefficient     NUMERIC(8, 4)   NOT NULL,
    PRIMARY KEY (settings_id, component_type),
    CONSTRAINT ck_pricing_coefficient CHECK (coefficient > 0)
);

INSERT INTO pricing_coefficient (settings_id, component_type, coefficient)
SELECT 1, type, 1.0000
FROM unnest(ARRAY['SUPPLY', 'LABOR', 'SERVICE', 'TRAVEL', 'RENTAL', 'SUBCONTRACT', 'LUMP_SUM', 'OTHER']) AS type;

CREATE TABLE pricing_rate (
    id          BIGSERIAL       PRIMARY KEY,
    code        VARCHAR(30)     NOT NULL,
    label       VARCHAR(200)    NOT NULL,
    type        VARCHAR(20)     NOT NULL,
    unit        VARCHAR(20)     NOT NULL,
    cost        NUMERIC(15, 4)  NOT NULL,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    updated_by  TEXT,
    updated_at  TIMESTAMPTZ     NOT NULL,
    CONSTRAINT uk_pricing_rate_code UNIQUE (code),
    CONSTRAINT ck_pricing_rate_cost CHECK (cost >= 0)
);

-- The rate a cost was copied from, kept as text: the rate may change or go.
ALTER TABLE estimate_component ADD COLUMN rate_code VARCHAR(30);
