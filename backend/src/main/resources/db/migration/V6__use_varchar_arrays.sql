-- Hibernate binds List<String> parameters as varchar[]: list columns use the
-- same type so they can be compared in queries (department filter).
ALTER TABLE tender
    ALTER COLUMN departments  TYPE VARCHAR[],
    ALTER COLUMN market_types TYPE VARCHAR[],
    ALTER COLUMN cpv_codes    TYPE VARCHAR[];

ALTER TABLE filter_criteria
    ALTER COLUMN departments       TYPE VARCHAR[],
    ALTER COLUMN market_types      TYPE VARCHAR[],
    ALTER COLUMN cpv_prefixes      TYPE VARCHAR[],
    ALTER COLUMN keywords          TYPE VARCHAR[],
    ALTER COLUMN excluded_keywords TYPE VARCHAR[];
