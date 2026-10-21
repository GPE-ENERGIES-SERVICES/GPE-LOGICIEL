-- Last publication day a collection asks for. A collection resuming after a
-- long stop covers a limited number of days, so the next one starts from
-- the day it reached rather than from the day it ran. The past runs asked
-- up to the day they started (Paris time).
-- Rollback: ALTER TABLE collect_run DROP COLUMN until_date;
ALTER TABLE collect_run ADD COLUMN until_date DATE;

UPDATE collect_run SET until_date = (started_at AT TIME ZONE 'Europe/Paris')::date;
