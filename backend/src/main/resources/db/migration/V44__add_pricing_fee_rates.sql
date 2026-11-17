-- Rates of the agency fees, overheads and service fees, each a share of the
-- sale price: they change no price, they give the net margin of the export.
-- Rollback: ALTER TABLE pricing_settings DROP COLUMN agency_fee_rate,
--   DROP COLUMN overhead_rate, DROP COLUMN service_fee_rate;
ALTER TABLE pricing_settings
    ADD COLUMN agency_fee_rate  NUMERIC(6, 4) NOT NULL DEFAULT 0.0500,
    ADD COLUMN overhead_rate    NUMERIC(6, 4) NOT NULL DEFAULT 0.0500,
    ADD COLUMN service_fee_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.0500,
    ADD CONSTRAINT ck_pricing_settings_fee_rates CHECK (
        agency_fee_rate >= 0 AND agency_fee_rate < 1
        AND overhead_rate >= 0 AND overhead_rate < 1
        AND service_fee_rate >= 0 AND service_fee_rate < 1);
