-- The technical solution of each item of an estimate (a line of the price
-- schedule), decided in the technical choices: what is installed and how, how
-- sure the information is, what the documents say with their source, what is
-- missing. Its resources are the components of the item: no other model.
-- Rollback: ALTER TABLE estimate_item DROP COLUMN tech_type, DROP COLUMN tech_characteristics,
--   DROP COLUMN tech_method, DROP COLUMN tech_status, DROP COLUMN tech_facts, DROP COLUMN tech_missing,
--   DROP COLUMN tech_origin, DROP COLUMN tech_validated_by, DROP COLUMN tech_validated_at;

ALTER TABLE estimate_item
    ADD COLUMN tech_type            VARCHAR(300),
    ADD COLUMN tech_characteristics TEXT,
    ADD COLUMN tech_method          TEXT,
    ADD COLUMN tech_status          VARCHAR(20),
    ADD COLUMN tech_facts           TEXT,
    ADD COLUMN tech_missing         TEXT,
    ADD COLUMN tech_origin          VARCHAR(10),
    ADD COLUMN tech_validated_by    TEXT,
    ADD COLUMN tech_validated_at    TIMESTAMPTZ,
    ADD CONSTRAINT ck_estimate_item_tech_status
        CHECK (tech_status IS NULL OR tech_status IN ('COMPLETE', 'TO_CONFIRM', 'INSUFFICIENT'));
