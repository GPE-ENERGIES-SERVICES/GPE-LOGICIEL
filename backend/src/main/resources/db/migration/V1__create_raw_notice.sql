CREATE TABLE raw_notice (
    id           BIGSERIAL PRIMARY KEY,
    source       VARCHAR(50)  NOT NULL,
    external_id  VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    published_at DATE,
    collected_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_raw_notice_source_external_id UNIQUE (source, external_id)
);
