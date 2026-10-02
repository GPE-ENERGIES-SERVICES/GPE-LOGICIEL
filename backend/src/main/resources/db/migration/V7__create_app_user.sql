CREATE TABLE app_user (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255)  NOT NULL,
    display_name   VARCHAR(100)  NOT NULL,
    password_hash  VARCHAR(100)  NOT NULL,
    role           VARCHAR(20)   NOT NULL,
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);
