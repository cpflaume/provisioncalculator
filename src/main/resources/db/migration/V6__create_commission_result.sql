CREATE TABLE commission_result (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(255)   NOT NULL,
    settlement_id           BIGINT         NOT NULL REFERENCES settlement(id),
    calculation_id          UUID           NOT NULL REFERENCES calculation(id),
    recipient_customer_id   VARCHAR(255)   NOT NULL,
    source_purchase_id      BIGINT         REFERENCES purchase(id),
    amount                  NUMERIC(15, 4) NOT NULL,
    depth                   INT,
    rule_id                 VARCHAR(255)   NOT NULL
);
