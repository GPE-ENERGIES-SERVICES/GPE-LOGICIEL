-- The reference data of GPE (« Entreprise »): identity, activities, staff
-- and their credentials, equipment, certifications, references, official
-- documents and economic parameters. The other modules read it; only an
-- administrator changes it. The costs of labour stay in pricing_rate (V32):
-- the economic part of the company, not a copy of it.
-- Rollback: DROP TABLE company_file, company_economic_parameter,
--   company_document, company_reference, company_certification, equipment,
--   employee_credential, employee, company_activity, company_contact,
--   company_profile;

CREATE TABLE company_profile (
    id                        SMALLINT        PRIMARY KEY,
    legal_name                VARCHAR(200),
    trade_name                VARCHAR(200),
    legal_form                VARCHAR(100),
    siren                     VARCHAR(9),
    siret                     VARCHAR(14),
    vat_number                VARCHAR(20),
    ape_code                  VARCHAR(10),
    rcs                       VARCHAR(100),
    share_capital             NUMERIC(15, 2),
    address_line              VARCHAR(300),
    postal_code               VARCHAR(10),
    city                      VARCHAR(100),
    country                   VARCHAR(100),
    phone                     VARCHAR(30),
    email                     VARCHAR(200),
    website                   VARCHAR(200),
    intervention_departments  VARCHAR[]       NOT NULL DEFAULT '{}',
    intervention_note         TEXT,
    updated_by                TEXT,
    updated_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_company_profile_single_row CHECK (id = 1)
);

INSERT INTO company_profile (id) VALUES (1);

CREATE TABLE company_contact (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(200)    NOT NULL,
    role        VARCHAR(200),
    phone       VARCHAR(30),
    email       VARCHAR(200),
    notes       TEXT,
    updated_by  TEXT,
    updated_at  TIMESTAMPTZ     NOT NULL
);

-- What GPE does. activity_code: a type of works known by the saved searches
-- (ELECTRICITY...), to compare with the tenders.
CREATE TABLE company_activity (
    id             BIGSERIAL       PRIMARY KEY,
    kind           VARCHAR(20)     NOT NULL,
    label          VARCHAR(200)    NOT NULL,
    description    TEXT,
    activity_code  VARCHAR(40),
    cpv_prefixes   VARCHAR[]       NOT NULL DEFAULT '{}',
    updated_by     TEXT,
    updated_at     TIMESTAMPTZ     NOT NULL
);

CREATE TABLE employee (
    id          BIGSERIAL       PRIMARY KEY,
    first_name  VARCHAR(100)    NOT NULL,
    last_name   VARCHAR(100)    NOT NULL,
    job_title   VARCHAR(200),
    email       VARCHAR(200),
    phone       VARCHAR(30),
    hired_on    DATE,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    skills      TEXT,
    experience  TEXT,
    updated_by  TEXT,
    updated_at  TIMESTAMPTZ     NOT NULL
);

-- Qualification, habilitation (électrique B2V, BR...), certificate or training of an employee.
CREATE TABLE employee_credential (
    id           BIGSERIAL       PRIMARY KEY,
    employee_id  BIGINT          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    kind         VARCHAR(20)     NOT NULL,
    label        VARCHAR(200)    NOT NULL,
    number       VARCHAR(100),
    issuer       VARCHAR(200),
    obtained_on  DATE,
    expires_on   DATE,
    updated_by   TEXT,
    updated_at   TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_employee_credential_employee ON employee_credential (employee_id);

CREATE TABLE equipment (
    id             BIGSERIAL       PRIMARY KEY,
    category       VARCHAR(20)     NOT NULL,
    label          VARCHAR(200)    NOT NULL,
    model          VARCHAR(200),
    identifier     VARCHAR(100),
    quantity       INTEGER         NOT NULL DEFAULT 1,
    status         VARCHAR(20)     NOT NULL,
    next_check_on  DATE,
    notes          TEXT,
    updated_by     TEXT,
    updated_at     TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_equipment_quantity CHECK (quantity >= 0)
);

-- Certification, qualification (Qualifelec, RGE...) or label of the company.
CREATE TABLE company_certification (
    id           BIGSERIAL       PRIMARY KEY,
    kind         VARCHAR(20)     NOT NULL,
    name         VARCHAR(200)    NOT NULL,
    organism     VARCHAR(200),
    number       VARCHAR(100),
    scope        TEXT,
    obtained_on  DATE,
    expires_on   DATE,
    updated_by   TEXT,
    updated_at   TIMESTAMPTZ     NOT NULL
);

-- A work done by GPE, given as a reference in a bid.
CREATE TABLE company_reference (
    id            BIGSERIAL       PRIMARY KEY,
    project_name  VARCHAR(300)    NOT NULL,
    client        VARCHAR(200),
    location      VARCHAR(200),
    nature        VARCHAR(300),
    amount        NUMERIC(15, 2),
    started_on    DATE,
    finished_on   DATE,
    description   TEXT,
    skills        TEXT,
    updated_by    TEXT,
    updated_at    TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_company_reference_amount CHECK (amount >= 0)
);

-- An official document (Kbis, insurance, attestations...); its file is the
-- last one uploaded, the former ones are kept as its history.
CREATE TABLE company_document (
    id          BIGSERIAL       PRIMARY KEY,
    category    VARCHAR(20)     NOT NULL,
    name        VARCHAR(200)    NOT NULL,
    issued_on   DATE,
    expires_on  DATE,
    notes       TEXT,
    updated_by  TEXT,
    updated_at  TIMESTAMPTZ     NOT NULL
);

-- A value of GPE for its prices (overheads, margin...), entered by GPE and
-- not used by any computation until its rule is decided.
CREATE TABLE company_economic_parameter (
    id          BIGSERIAL       PRIMARY KEY,
    label       VARCHAR(200)    NOT NULL,
    value       NUMERIC(15, 4),
    unit        VARCHAR(30),
    note        TEXT,
    updated_by  TEXT,
    updated_at  TIMESTAMPTZ     NOT NULL
);

-- A file of the company, kept in the storage directory, attached to what it
-- proves: the logo, a document, a certification, a credential, a reference
-- or an equipment. Removed with it by the application.
CREATE TABLE company_file (
    id            BIGSERIAL       PRIMARY KEY,
    owner_type    VARCHAR(20)     NOT NULL,
    owner_id      BIGINT          NOT NULL,
    file_name     TEXT            NOT NULL,
    storage_path  TEXT            NOT NULL,
    size_bytes    BIGINT          NOT NULL,
    uploaded_by   TEXT,
    uploaded_at   TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_company_file_owner ON company_file (owner_type, owner_id, id);
