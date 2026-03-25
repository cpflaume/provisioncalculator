CREATE TABLE tree_node (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(255) NOT NULL,
    settlement_id   BIGINT       NOT NULL REFERENCES settlement(id),
    customer_id     VARCHAR(255) NOT NULL,
    parent_node_id  BIGINT       REFERENCES tree_node(id),
    UNIQUE (tenant_id, settlement_id, customer_id)
);
