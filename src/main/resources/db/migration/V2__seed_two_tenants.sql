-- Two tenants with overlapping data on purpose: same email in both, documents with the
-- same titles. Isolation that is only tested against data that could not collide anyway
-- has not been tested.

INSERT INTO tenants (slug, name) VALUES
    ('acme', 'ACME Industries'),
    ('globex', 'Globex Corporation');

-- BCrypt hash of 'secret123' for every seeded user.
INSERT INTO users (tenant_id, email, password_hash, department, clearance_level, role) VALUES
    (1, 'ana@example.com',   '$2a$10$.5UVcySD7PZUh3LPNHJ3cOnm3vS8A93ID9zArKVhLpdjbbKnRQoPC', 'FINANCE',     3, 'ROLE_USER'),
    (1, 'bruno@example.com', '$2a$10$.5UVcySD7PZUh3LPNHJ3cOnm3vS8A93ID9zArKVhLpdjbbKnRQoPC', 'ENGINEERING', 1, 'ROLE_USER'),
    (1, 'admin@example.com', '$2a$10$.5UVcySD7PZUh3LPNHJ3cOnm3vS8A93ID9zArKVhLpdjbbKnRQoPC', 'FINANCE',     5, 'ROLE_ADMIN'),
    -- Same address as the ACME user, different company. Only the composite unique
    -- constraint allows this, and multi-tenant systems need it to.
    (2, 'ana@example.com',   '$2a$10$.5UVcySD7PZUh3LPNHJ3cOnm3vS8A93ID9zArKVhLpdjbbKnRQoPC', 'FINANCE',     4, 'ROLE_USER');

INSERT INTO documents (tenant_id, owner_id, title, content, required_clearance, department) VALUES
    (1, 1, 'Quarterly results',   'ACME numbers',            3, 'FINANCE'),
    (1, 1, 'Public handbook',     'ACME handbook',           1, NULL),
    (1, 2, 'Architecture notes',  'ACME architecture',       1, 'ENGINEERING'),
    (1, 3, 'Board minutes',       'ACME board minutes',      5, 'FINANCE'),
    (2, 4, 'Quarterly results',   'Globex numbers',          3, 'FINANCE'),
    (2, 4, 'Public handbook',     'Globex handbook',         1, NULL);
