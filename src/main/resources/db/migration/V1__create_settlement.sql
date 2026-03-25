CREATE TABLE settlement (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    status          VARCHAR(50)  NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
