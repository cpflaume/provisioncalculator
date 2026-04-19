package com.provisions.calculator.model

import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(name = "tree_node", uniqueConstraints = [UniqueConstraint(columnNames = ["tenant_id", "settlement_id", "customer_id"])])
class TreeNode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id", nullable = false)
    val settlement: Settlement,

    @Column(name = "customer_id", nullable = false)
    val customerId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_node_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    val parentNode: TreeNode? = null
)
