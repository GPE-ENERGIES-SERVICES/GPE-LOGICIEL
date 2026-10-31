-- Estimates (« chiffrages ») of a tender, or of one of its lots: items sold,
-- aligned on the price schedule (DPGF, BPU, DQE), each made of the costs of
-- one unit (supplies, labour, rental...). Every figure is entered by a user or
-- computed by the application; costs are copied in the estimate, never read
-- again from elsewhere, so that a later change of a price leaves it as it is.
-- Rollback: DROP TABLE estimate_component; DROP TABLE estimate_item;
--   DROP TABLE estimate_coefficient; DROP TABLE estimate;
CREATE TABLE estimate (
    id               BIGSERIAL       PRIMARY KEY,
    tender_id        BIGINT          NOT NULL REFERENCES tender (id),
    -- The number of the lot, not its id: lots are written again by each rectification.
    lot_number       VARCHAR(50),
    version          INTEGER         NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    label            VARCHAR(200),
    vat_rate         NUMERIC(6, 4)   NOT NULL,
    -- Computed at every change
    total_cost       NUMERIC(15, 2),
    total_ht         NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    total_vat        NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    total_ttc        NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    options_ht       NUMERIC(15, 2)  NOT NULL DEFAULT 0,
    incomplete_items INTEGER         NOT NULL DEFAULT 0,
    created_by       TEXT,
    created_at       TIMESTAMPTZ     NOT NULL,
    updated_by       TEXT,
    updated_at       TIMESTAMPTZ     NOT NULL,
    validated_by     TEXT,
    validated_at     TIMESTAMPTZ,
    CONSTRAINT ck_estimate_vat_rate CHECK (vat_rate >= 0 AND vat_rate < 1),
    CONSTRAINT ck_estimate_version CHECK (version >= 1)
);

-- No lot stands for the whole tender: NULL would never be equal.
CREATE UNIQUE INDEX uk_estimate_version ON estimate (tender_id, COALESCE(lot_number, ''), version);
-- At most one draft per tender and lot.
CREATE UNIQUE INDEX uk_estimate_draft ON estimate (tender_id, COALESCE(lot_number, '')) WHERE status = 'DRAFT';

-- Sale coefficient of each type of cost: sale price = cost x coefficient.
CREATE TABLE estimate_coefficient (
    estimate_id     BIGINT          NOT NULL REFERENCES estimate (id) ON DELETE CASCADE,
    component_type  VARCHAR(20)     NOT NULL,
    coefficient     NUMERIC(8, 4)   NOT NULL,
    PRIMARY KEY (estimate_id, component_type),
    CONSTRAINT ck_estimate_coefficient CHECK (coefficient > 0)
);

CREATE TABLE estimate_item (
    id                BIGSERIAL       PRIMARY KEY,
    estimate_id       BIGINT          NOT NULL REFERENCES estimate (id) ON DELETE CASCADE,
    position          INTEGER         NOT NULL,
    chapter           VARCHAR(200),
    reference         VARCHAR(50),
    designation       TEXT            NOT NULL,
    unit              VARCHAR(20),
    quantity          NUMERIC(15, 3),
    -- COMPOSED: price from the costs; FIXED: price entered; TEXT: PM, inclus...
    price_mode        VARCHAR(10)     NOT NULL,
    fixed_unit_price  NUMERIC(15, 2),
    text_price        VARCHAR(30),
    is_option         BOOLEAN         NOT NULL DEFAULT FALSE,
    -- Computed at every change
    unit_cost         NUMERIC(15, 4),
    unit_price        NUMERIC(15, 2),
    amount            NUMERIC(15, 2),
    CONSTRAINT ck_estimate_item_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_estimate_item_fixed_price CHECK (fixed_unit_price >= 0)
);

CREATE INDEX idx_estimate_item_estimate ON estimate_item (estimate_id, position);

-- Cost of one unit of the item.
CREATE TABLE estimate_component (
    id                BIGSERIAL       PRIMARY KEY,
    item_id           BIGINT          NOT NULL REFERENCES estimate_item (id) ON DELETE CASCADE,
    position          INTEGER         NOT NULL,
    type              VARCHAR(20)     NOT NULL,
    label             VARCHAR(300)    NOT NULL,
    quantity          NUMERIC(15, 4)  NOT NULL,
    unit              VARCHAR(20),
    unit_cost         NUMERIC(15, 4),
    waste_rate        NUMERIC(6, 4)   NOT NULL DEFAULT 0,
    -- NULL: the coefficient of the estimate for its type
    coefficient       NUMERIC(8, 4),
    supplier_name     VARCHAR(100),
    supplier_ref      VARCHAR(100),
    manufacturer_ref  VARCHAR(100),
    cost_date         DATE,
    CONSTRAINT ck_estimate_component_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_estimate_component_cost CHECK (unit_cost >= 0),
    CONSTRAINT ck_estimate_component_waste CHECK (waste_rate >= 0 AND waste_rate < 1),
    CONSTRAINT ck_estimate_component_coefficient CHECK (coefficient > 0)
);

CREATE INDEX idx_estimate_component_item ON estimate_component (item_id, position);
