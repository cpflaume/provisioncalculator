CREATE TABLE calculation (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    settlement_id   BIGINT       NOT NULL REFERENCES settlement(id),
    input_hash      VARCHAR(64)  NOT NULL,
    calculated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, settlement_id, input_hash)
);
