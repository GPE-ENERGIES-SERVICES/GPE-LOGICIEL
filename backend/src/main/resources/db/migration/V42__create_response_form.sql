-- The forms of the consultation file filled for an answer (DC1, DC2, acte
-- d'engagement): what each place of the form holds and where it comes from. A
-- value a user wrote is never changed by a new filling.
-- Rollback: DROP TABLE response_form_field, response_form;
--   ALTER TABLE company_profile DROP COLUMN iban, DROP COLUMN bic,
--     DROP COLUMN signatory_name, DROP COLUMN signatory_title;

ALTER TABLE company_profile
    ADD COLUMN iban             VARCHAR(50),
    ADD COLUMN bic              VARCHAR(11),
    ADD COLUMN signatory_name   VARCHAR(200),
    ADD COLUMN signatory_title  VARCHAR(200);

CREATE TABLE response_form (
    id           BIGSERIAL     PRIMARY KEY,
    piece_id     BIGINT        NOT NULL UNIQUE REFERENCES response_piece (id) ON DELETE CASCADE,
    document_id  BIGINT        NOT NULL REFERENCES document (id),
    lot_number   VARCHAR(50),
    updated_by   TEXT,
    updated_at   TIMESTAMPTZ   NOT NULL
);

-- source: COMPANY, ESTIMATE, TENDER, AI, MANUAL, MISSING or NONE.
CREATE TABLE response_form_field (
    id        BIGSERIAL      PRIMARY KEY,
    form_id   BIGINT         NOT NULL REFERENCES response_form (id) ON DELETE CASCADE,
    position  INTEGER        NOT NULL,
    slot_id   VARCHAR(30)    NOT NULL,
    kind      VARCHAR(10)    NOT NULL,
    label     TEXT           NOT NULL,
    value     TEXT,
    checked   BOOLEAN        NOT NULL DEFAULT FALSE,
    source    VARCHAR(20)    NOT NULL,
    note      TEXT,
    skipped   BOOLEAN        NOT NULL DEFAULT FALSE,
    locked    BOOLEAN        NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_response_form_field UNIQUE (form_id, slot_id)
);
