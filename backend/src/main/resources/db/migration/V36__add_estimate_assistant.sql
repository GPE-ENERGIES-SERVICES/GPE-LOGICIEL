-- Assistance of an AI to the estimates: its proposals, each decided by a
-- user, and the calls made outside the analyses, counted in the monthly
-- ceiling of the AI with the analyses. Off until the AI analysis is enabled.
-- Rollback: DROP TABLE estimate_suggestion; DROP TABLE ai_call;
CREATE TABLE ai_call (
    id             BIGSERIAL       PRIMARY KEY,
    kind           VARCHAR(30)     NOT NULL,
    model          TEXT,
    input_tokens   INTEGER         NOT NULL,
    output_tokens  INTEGER         NOT NULL,
    created_by     TEXT,
    created_at     TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_ai_call_created_at ON ai_call (created_at);

-- SUPPLY: an offer of the imported catalogues for an item, and its quantity
-- per unit; never a price, copied from the offer once accepted. CHECK: a
-- point a user should check. Nothing changes the estimate until accepted.
CREATE TABLE estimate_suggestion (
    id           BIGSERIAL       PRIMARY KEY,
    estimate_id  BIGINT          NOT NULL REFERENCES estimate (id) ON DELETE CASCADE,
    item_id      BIGINT          REFERENCES estimate_item (id) ON DELETE CASCADE,
    kind         VARCHAR(10)     NOT NULL,
    offer_id     BIGINT,
    quantity     NUMERIC(15, 4),
    text         TEXT            NOT NULL,
    status       VARCHAR(10)     NOT NULL,
    model        TEXT,
    created_at   TIMESTAMPTZ     NOT NULL,
    decided_by   TEXT,
    decided_at   TIMESTAMPTZ
);

CREATE INDEX idx_estimate_suggestion_estimate ON estimate_suggestion (estimate_id, id);
