-- Saved searches (« veilles ») of the users. A consultation matches a saved
-- search when it meets all its criteria; its keywords, departments, market
-- types, activities and CPV prefixes are alternatives within each criterion.
-- Deleting a saved search never deletes a tender, a document nor a link.
-- Rollback: DROP TABLE alert_revision; DROP TABLE alert;
CREATE TABLE alert (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES app_user (id),
    name            VARCHAR(100)    NOT NULL,
    keywords        VARCHAR[]       NOT NULL DEFAULT '{}',
    region          VARCHAR(100),
    departments     VARCHAR[]       NOT NULL DEFAULT '{}',
    market_types    VARCHAR[]       NOT NULL DEFAULT '{}',
    activities      VARCHAR[]       NOT NULL DEFAULT '{}',
    cpv_prefixes    VARCHAR[]       NOT NULL DEFAULT '{}',
    min_amount      NUMERIC(15, 2),
    max_amount      NUMERIC(15, 2),
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    last_viewed_at  TIMESTAMPTZ
);

CREATE INDEX idx_alert_user ON alert (user_id);

-- Incremented at every change of a saved search (creation, criteria,
-- activation, deletion): an eligibility evaluated with an older revision is
-- out of date.
CREATE TABLE alert_revision (
    id        SMALLINT  PRIMARY KEY,
    revision  BIGINT    NOT NULL,
    CONSTRAINT ck_alert_revision_single_row CHECK (id = 1)
);

INSERT INTO alert_revision (id, revision) VALUES (1, 0);
