-- Tenders followed by each user; the follow-up status stays shared by the team.
CREATE TABLE tender_follow (
    user_id     BIGINT       NOT NULL REFERENCES app_user (id),
    tender_id   BIGINT       NOT NULL REFERENCES tender (id),
    created_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (user_id, tender_id)
);

CREATE INDEX idx_tender_follow_tender ON tender_follow (tender_id);
