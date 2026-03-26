CREATE INDEX idx_tree_node_tenant_settlement   ON tree_node (tenant_id, settlement_id);
CREATE INDEX idx_tree_node_parent              ON tree_node (parent_node_id);
CREATE INDEX idx_purchase_tenant_settlement    ON purchase  (tenant_id, settlement_id);
CREATE INDEX idx_calculation_tenant_settlement ON calculation (tenant_id, settlement_id);
CREATE INDEX idx_result_calculation            ON commission_result (calculation_id);
CREATE INDEX idx_result_recipient              ON commission_result (calculation_id, recipient_customer_id);
CREATE INDEX idx_result_source_purchase        ON commission_result (source_purchase_id);
