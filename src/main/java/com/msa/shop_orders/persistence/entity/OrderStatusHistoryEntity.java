package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_status_history")
@Getter
@Setter
public class OrderStatusHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "old_status", length = 40)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 40)
    private String newStatus;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "refund_policy_applied", length = 40)
    private String refundPolicyApplied;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
