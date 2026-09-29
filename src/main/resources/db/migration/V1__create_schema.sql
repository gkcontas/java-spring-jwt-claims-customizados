CREATE TABLE tenants (
    id     BIGSERIAL    PRIMARY KEY,
    slug   VARCHAR(60)  NOT NULL UNIQUE,
    name   VARCHAR(160) NOT NULL,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE users (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL REFERENCES tenants (id),
    email           VARCHAR(200) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    department      VARCHAR(60)  NOT NULL,
    clearance_level INTEGER      NOT NULL CHECK (clearance_level BETWEEN 1 AND 5),
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN')),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Unique per tenant, not globally: two different companies may both employ an
    -- ana@example.com, and a global unique constraint would let one tenant's sign-up
    -- reveal that the other has a user with that address.
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE TABLE documents (
    id                 BIGSERIAL    PRIMARY KEY,
    -- Every resource carries its tenant. This column is the basis of the isolation, and
    -- the reason it is NOT NULL is that a row without it belongs to everyone.
    tenant_id          BIGINT       NOT NULL REFERENCES tenants (id),
    owner_id           BIGINT       NOT NULL REFERENCES users (id),
    title              VARCHAR(200) NOT NULL,
    content            TEXT         NOT NULL,
    required_clearance INTEGER      NOT NULL CHECK (required_clearance BETWEEN 1 AND 5),
    department         VARCHAR(60)
);

CREATE INDEX idx_users_tenant ON users (tenant_id);
CREATE INDEX idx_documents_tenant ON documents (tenant_id);
