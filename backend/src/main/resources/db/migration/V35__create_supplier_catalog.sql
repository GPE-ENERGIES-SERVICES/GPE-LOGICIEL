-- Suppliers of GPE and their prices, imported from a price file (CSV, XLSX)
-- given by the supplier: no supplier is ever contacted by the application.
-- An offer holds the last price imported for a reference; an estimate copies
-- the price it uses, with its date, so that a new import never changes it.
-- Rollback: ALTER TABLE estimate_component DROP COLUMN offer_id;
--   DROP TABLE supplier_offer; DROP TABLE supplier;
CREATE TABLE supplier (
    id          BIGSERIAL       PRIMARY KEY,
    code        VARCHAR(30)     NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    CONSTRAINT uk_supplier_code UNIQUE (code)
);

CREATE TABLE supplier_offer (
    id                BIGSERIAL       PRIMARY KEY,
    supplier_id       BIGINT          NOT NULL REFERENCES supplier (id) ON DELETE CASCADE,
    supplier_ref      VARCHAR(100)    NOT NULL,
    manufacturer_ref  VARCHAR(100),
    ean               VARCHAR(20),
    designation       TEXT            NOT NULL,
    purchase_unit     VARCHAR(20),
    -- Units sold per price: 100 for a price per reel of 100 m
    pack_quantity     NUMERIC(15, 4)  NOT NULL DEFAULT 1,
    list_price        NUMERIC(15, 4),
    discount_rate     NUMERIC(6, 4),
    net_price         NUMERIC(15, 4)  NOT NULL,
    valid_from        DATE,
    valid_until       DATE,
    imported_at       TIMESTAMPTZ     NOT NULL,
    imported_by       TEXT,
    CONSTRAINT uk_supplier_offer_ref UNIQUE (supplier_id, supplier_ref),
    CONSTRAINT ck_supplier_offer_pack CHECK (pack_quantity > 0),
    CONSTRAINT ck_supplier_offer_price CHECK (net_price >= 0)
);

CREATE INDEX idx_supplier_offer_manufacturer_ref ON supplier_offer (manufacturer_ref);
CREATE INDEX idx_supplier_offer_ean ON supplier_offer (ean);

-- The offer a cost was copied from: no constraint, the offer may be replaced.
ALTER TABLE estimate_component ADD COLUMN offer_id BIGINT;
