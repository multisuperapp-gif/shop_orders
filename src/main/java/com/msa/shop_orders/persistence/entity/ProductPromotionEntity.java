package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_promotions")
@Getter
@Setter
public class ProductPromotionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "promotion_type", nullable = false, length = 40)
    private String promotionType;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "priority_score", nullable = false)
    private Integer priorityScore;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
