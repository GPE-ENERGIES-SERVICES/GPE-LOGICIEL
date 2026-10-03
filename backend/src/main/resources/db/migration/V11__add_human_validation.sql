-- Decision of a user on an extracted value. It prevails over the value of the
-- model, which is kept unchanged (rule RM10).
ALTER TABLE extracted_field ADD COLUMN decision VARCHAR(20);
ALTER TABLE extracted_field ADD COLUMN corrected_value TEXT;
ALTER TABLE extracted_field ADD COLUMN decided_by TEXT;
ALTER TABLE extracted_field ADD COLUMN decided_at TIMESTAMPTZ;

-- The whole summary sheet validated by a user.
ALTER TABLE analysis ADD COLUMN validated_by TEXT;
ALTER TABLE analysis ADD COLUMN validated_at TIMESTAMPTZ;
