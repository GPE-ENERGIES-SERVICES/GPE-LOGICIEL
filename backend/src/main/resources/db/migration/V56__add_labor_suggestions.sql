-- A labour time proposed for an item (LABOR): the rate of GPE it is counted
-- at, and the hours per unit of the item in quantity; its cost is copied from
-- the rate once accepted, never written by the AI.
-- Rollback: ALTER TABLE estimate_suggestion DROP COLUMN rate_code;
ALTER TABLE estimate_suggestion ADD COLUMN rate_code VARCHAR(30);
