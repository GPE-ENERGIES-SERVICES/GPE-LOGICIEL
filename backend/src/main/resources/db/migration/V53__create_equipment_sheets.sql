-- The technical sheets of the equipment GPE installs (modules, inverters,
-- mounting systems...), one PDF each, and the equipment chosen for a tender:
-- its sheets go with the offer, its list makes the equipment table of the
-- memoir. Deleting a sheet takes it out of the tenders.
-- Rollback: DROP TABLE tender_equipment; DROP TABLE equipment_sheet;
CREATE TABLE equipment_sheet (
    id            BIGSERIAL     PRIMARY KEY,
    category      VARCHAR(30)   NOT NULL CHECK (category IN ('MODULE', 'INVERTER', 'MOUNTING', 'MONITORING', 'CABLE_DC',
                                    'CABLE_AC', 'ELECTRICAL_BOX', 'CONNECTOR', 'SAFETY', 'CABLE_TRAY', 'OTHER')),
    brand         VARCHAR(100),
    reference     VARCHAR(200)  NOT NULL,
    file_name     VARCHAR(255)  NOT NULL,
    storage_path  TEXT          NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    sha256        VARCHAR(64)   NOT NULL UNIQUE,
    uploaded_by   TEXT,
    uploaded_at   TIMESTAMPTZ   NOT NULL
);

CREATE TABLE tender_equipment (
    tender_id   BIGINT       NOT NULL REFERENCES tender (id) ON DELETE CASCADE,
    sheet_id    BIGINT       NOT NULL REFERENCES equipment_sheet (id) ON DELETE CASCADE,
    chosen_by   TEXT,
    chosen_at   TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (tender_id, sheet_id)
);
