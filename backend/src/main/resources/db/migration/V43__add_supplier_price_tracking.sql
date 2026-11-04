-- Prices read from the service of a supplier (Rexel), beside the imported files,
-- and the history of the prices of an offer: an offer keeps its last price, a
-- line is added here each time this price changes. "manufacturer_code" is the
-- code of the manufacturer at the supplier: "supplierCode" for Rexel, whose
-- "supplier" is the manufacturer when ours is the distributor.
-- Rollback: DROP TABLE supplier_offer_price; DROP INDEX idx_supplier_offer_manufacturer_key;
--   ALTER TABLE supplier_offer DROP COLUMN manufacturer_code, DROP COLUMN eco_tax,
--   DROP COLUMN price_source, DROP COLUMN priced_quantity;
ALTER TABLE supplier_offer
    ADD COLUMN manufacturer_code  VARCHAR(10),
    ADD COLUMN eco_tax            NUMERIC(15, 4),
    ADD COLUMN price_source       VARCHAR(20)  NOT NULL DEFAULT 'IMPORT',
    -- Quantity the price was asked for: it may depend on it
    ADD COLUMN priced_quantity    INTEGER;

CREATE INDEX idx_supplier_offer_manufacturer_key
    ON supplier_offer (supplier_id, manufacturer_code, manufacturer_ref);

CREATE TABLE supplier_offer_price (
    id                 BIGSERIAL       PRIMARY KEY,
    offer_id           BIGINT          NOT NULL REFERENCES supplier_offer (id) ON DELETE CASCADE,
    list_price         NUMERIC(15, 4),
    discount_rate      NUMERIC(6, 4),
    net_price          NUMERIC(15, 4)  NOT NULL,
    eco_tax            NUMERIC(15, 4),
    pack_quantity      NUMERIC(15, 4)  NOT NULL,
    ordering_quantity  INTEGER,
    condition_label    TEXT,
    price_label        TEXT,
    sales_agreement    VARCHAR(100),
    -- IMPORT, or the service read: REXEL_TEST, REXEL_PROD
    source             VARCHAR(20)     NOT NULL,
    observed_at        TIMESTAMPTZ     NOT NULL,
    observed_by        TEXT
);

CREATE INDEX idx_supplier_offer_price_offer ON supplier_offer_price (offer_id, observed_at DESC, id DESC);

-- The prices known today start the history: the first refresh must not lose them.
INSERT INTO supplier_offer_price (offer_id, list_price, discount_rate, net_price, pack_quantity, source, observed_at, observed_by)
SELECT id, list_price, discount_rate, net_price, pack_quantity, 'IMPORT', imported_at, imported_by
FROM supplier_offer;
