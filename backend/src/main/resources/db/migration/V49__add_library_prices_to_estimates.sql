-- A unit price taken from the price library for an item, proposed by the AI
-- (suggestion PRICE) or picked by a user, updated from the year of the price
-- by an annual rate set by an administrator (3 % a year by default).
-- Rollback: ALTER TABLE estimate_suggestion DROP COLUMN library_item_id, DROP COLUMN price;
--   ALTER TABLE pricing_settings DROP COLUMN price_index_rate;
ALTER TABLE estimate_suggestion
    ADD COLUMN library_item_id BIGINT REFERENCES price_library_item (id) ON DELETE CASCADE,
    ADD COLUMN price           NUMERIC(15, 2);

ALTER TABLE pricing_settings
    ADD COLUMN price_index_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.0300,
    ADD CONSTRAINT ck_pricing_settings_price_index CHECK (price_index_rate > -1 AND price_index_rate < 1);
