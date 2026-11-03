-- Businesses are the tenant boundary: every other business-owned row carries business_id.
CREATE TABLE businesses
(
    id                  uuid PRIMARY KEY,
    name                varchar(200) NOT NULL,
    registration_number varchar(30),
    vat_number          varchar(20),
    email               varchar(254),
    phone               varchar(30),
    line1               varchar(200),
    line2               varchar(200),
    city                varchar(100),
    province            varchar(100),
    postal_code         varchar(20),
    country             varchar(100),
    currency            varchar(3)   NOT NULL,
    payment_terms_days  integer      NOT NULL CHECK (payment_terms_days BETWEEN 0 AND 365),
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    version             bigint       NOT NULL
);

CREATE TABLE users
(
    id            uuid PRIMARY KEY,
    business_id   uuid         NOT NULL REFERENCES businesses (id),
    email         varchar(254) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    full_name     varchar(200) NOT NULL,
    created_at    timestamptz  NOT NULL,
    last_login_at timestamptz,
    version       bigint       NOT NULL
);
CREATE INDEX ix_users_business ON users (business_id);

-- Only SHA-256 hashes of refresh tokens are stored.
CREATE TABLE refresh_tokens
(
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    created_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid,
    version     bigint      NOT NULL
);
CREATE INDEX ix_refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

CREATE TABLE customers
(
    id          uuid PRIMARY KEY,
    business_id uuid         NOT NULL REFERENCES businesses (id),
    name        varchar(200) NOT NULL,
    email       varchar(254) NOT NULL,
    phone       varchar(30),
    line1       varchar(200),
    line2       varchar(200),
    city        varchar(100),
    province    varchar(100),
    postal_code varchar(20),
    country     varchar(100),
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL
);
CREATE INDEX ix_customers_business_name ON customers (business_id, lower(name));

CREATE TABLE invoices
(
    id                  uuid PRIMARY KEY,
    business_id         uuid           NOT NULL REFERENCES businesses (id),
    customer_id         uuid           NOT NULL REFERENCES customers (id),
    number              varchar(30)    NOT NULL,
    issue_date          date           NOT NULL,
    due_date            date           NOT NULL,
    status              varchar(20)    NOT NULL
        CHECK (status IN ('DRAFT', 'SENT', 'PARTIALLY_PAID', 'OVERDUE', 'PAID', 'CANCELLED')),
    currency            varchar(3)     NOT NULL,
    notes               varchar(2000),
    subtotal            numeric(14, 2) NOT NULL,
    vat_total           numeric(14, 2) NOT NULL,
    total               numeric(14, 2) NOT NULL,
    amount_paid         numeric(14, 2) NOT NULL,
    sent_at             timestamptz,
    paid_at             timestamptz,
    overdue_at          timestamptz,
    cancelled_at        timestamptz,
    cancellation_reason varchar(500),
    created_at          timestamptz    NOT NULL,
    updated_at          timestamptz    NOT NULL,
    version             bigint         NOT NULL,
    CONSTRAINT uq_invoices_business_number UNIQUE (business_id, number),
    CONSTRAINT ck_invoices_due_after_issue CHECK (due_date >= issue_date),
    CONSTRAINT ck_invoices_amount_paid CHECK (amount_paid >= 0 AND amount_paid <= total)
);
CREATE INDEX ix_invoices_business_status ON invoices (business_id, status);
CREATE INDEX ix_invoices_business_issue_date ON invoices (business_id, issue_date DESC);
CREATE INDEX ix_invoices_business_customer ON invoices (business_id, customer_id);
-- Supports the overdue job's cross-tenant scan of open invoices by due date.
CREATE INDEX ix_invoices_open_due_date ON invoices (due_date)
    WHERE status IN ('SENT', 'PARTIALLY_PAID', 'OVERDUE');

CREATE TABLE invoice_line_items
(
    id            uuid PRIMARY KEY,
    invoice_id    uuid           NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    position      integer        NOT NULL,
    description   varchar(500)   NOT NULL,
    quantity      numeric(12, 3) NOT NULL CHECK (quantity > 0),
    unit_price    numeric(14, 2) NOT NULL CHECK (unit_price >= 0),
    vat_rate      numeric(5, 4)  NOT NULL CHECK (vat_rate BETWEEN 0 AND 1),
    line_subtotal numeric(14, 2) NOT NULL,
    line_vat      numeric(14, 2) NOT NULL,
    line_total    numeric(14, 2) NOT NULL,
    version       bigint         NOT NULL
);
CREATE INDEX ix_line_items_invoice ON invoice_line_items (invoice_id);

CREATE TABLE payments
(
    id              uuid PRIMARY KEY,
    invoice_id      uuid           NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    amount          numeric(14, 2) NOT NULL CHECK (amount > 0),
    paid_on         date           NOT NULL,
    method          varchar(20)    NOT NULL CHECK (method IN ('EFT', 'CARD', 'CASH', 'OTHER')),
    reference       varchar(100),
    idempotency_key varchar(100)   NOT NULL,
    created_at      timestamptz    NOT NULL,
    version         bigint         NOT NULL,
    -- Makes "record payment" safe to retry even under concurrent requests.
    CONSTRAINT uq_payments_idempotency UNIQUE (invoice_id, idempotency_key)
);
CREATE INDEX ix_payments_paid_on ON payments (paid_on);

CREATE TABLE invoice_activities
(
    id          uuid PRIMARY KEY,
    invoice_id  uuid          NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    type        varchar(30)   NOT NULL,
    message     varchar(1000) NOT NULL,
    occurred_at timestamptz   NOT NULL,
    version     bigint        NOT NULL
);
CREATE INDEX ix_activities_invoice ON invoice_activities (invoice_id, occurred_at);

-- Notification outbox. Rows are written by the overdue job and drained by the dispatcher.
CREATE TABLE notifications
(
    id             uuid PRIMARY KEY,
    business_id    uuid          NOT NULL REFERENCES businesses (id),
    invoice_id     uuid REFERENCES invoices (id),
    invoice_number varchar(30),
    kind           varchar(30)   NOT NULL CHECK (kind IN ('PAYMENT_REMINDER', 'OVERDUE_ALERT')),
    stage          varchar(10)   NOT NULL,
    channel        varchar(10)   NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'IN_APP')),
    recipient      varchar(254)  NOT NULL,
    subject        varchar(300)  NOT NULL,
    body           varchar(4000) NOT NULL,
    status         varchar(10)   NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts       integer       NOT NULL DEFAULT 0,
    last_error     varchar(500),
    created_at     timestamptz   NOT NULL,
    sent_at        timestamptz,
    read_at        timestamptz,
    version        bigint        NOT NULL,
    -- Guarantees a reminder stage is never generated twice for the same invoice and channel.
    CONSTRAINT uq_notifications_invoice_stage_channel UNIQUE (invoice_id, stage, channel)
);
CREATE INDEX ix_notifications_business_created ON notifications (business_id, created_at DESC);
CREATE INDEX ix_notifications_business_unread ON notifications (business_id) WHERE read_at IS NULL;
CREATE INDEX ix_notifications_pending ON notifications (created_at) WHERE status = 'PENDING';

-- Gap-free invoice numbering per business and year, allocated with an atomic upsert.
CREATE TABLE invoice_number_sequences
(
    business_id uuid    NOT NULL REFERENCES businesses (id),
    year        integer NOT NULL,
    last_value  integer NOT NULL,
    PRIMARY KEY (business_id, year)
);
