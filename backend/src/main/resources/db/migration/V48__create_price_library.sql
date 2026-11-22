-- The price library of GPE: the unit prices of the price schedules (DPGF,
-- BPU, DQE) GPE already filled and sent, one import per file, so that a new
-- estimate can start from the prices GPE really gave. Sale prices, never
-- costs. Deleting an import deletes its lines.
-- Rollback: DROP TABLE price_library_item; DROP TABLE price_library_import;
CREATE TABLE price_library_import (
    id           BIGSERIAL     PRIMARY KEY,
    label        VARCHAR(200)  NOT NULL,
    file_name    VARCHAR(255)  NOT NULL,
    price_year   INTEGER       CHECK (price_year BETWEEN 1990 AND 2100),
    domain       VARCHAR(20)   CHECK (domain IN ('ELECTRICITY', 'PHOTOVOLTAIC')),
    item_count   INTEGER       NOT NULL,
    imported_by  TEXT,
    imported_at  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE price_library_item (
    id           BIGSERIAL      PRIMARY KEY,
    import_id    BIGINT         NOT NULL REFERENCES price_library_import (id) ON DELETE CASCADE,
    sheet        VARCHAR(100),
    source_row   INTEGER        NOT NULL,
    chapter      TEXT,
    reference    VARCHAR(100),
    designation  TEXT           NOT NULL,
    unit         VARCHAR(30),
    quantity     NUMERIC(15, 4),
    unit_price   NUMERIC(15, 4) NOT NULL CHECK (unit_price > 0)
);

CREATE INDEX idx_price_library_item_import ON price_library_item (import_id);
