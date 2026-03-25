CREATE TABLE purchase (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(255)   NOT NULL,
    settlement_id       BIGINT         NOT NULL REFERENCES settlement(id),
    buyer_customer_id   VARCHAR(255)   NOT NULL,
    amount              NUMERIC(15, 4) NOT NULL,
    purchased_at        TIMESTAMP      NOT NULL
);
