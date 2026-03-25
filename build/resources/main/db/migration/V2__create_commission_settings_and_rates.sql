CREATE TABLE commission_settings (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    settlement_id   BIGINT       NOT NULL REFERENCES settlement(id),
    UNIQUE (tenant_id, settlement_id)
);

CREATE TABLE commission_rate (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(255)   NOT NULL,
    settings_id     BIGINT         NOT NULL REFERENCES commission_settings(id),
    depth           INT            NOT NULL,
    rate_percent    NUMERIC(8, 4)  NOT NULL,
    UNIQUE (settings_id, depth)
);
