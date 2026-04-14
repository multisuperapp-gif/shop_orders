package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shops")
@Getter
@Setter
public class ShopEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "shop_code", nullable = false, length = 32)
    private String shopCode;

    @Column(name = "registered_owner_name", nullable = false, length = 150)
    private String ownerName;

    @Column(name = "shop_name", length = 180)
    private String shopName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_type_id")
    private ShopTypeEntity shopType;

    @Column(name = "license_number", length = 80)
    private String licenseNumber;

    @Column(name = "approval_status", nullable = false, length = 120)
    private String approvalStatus;

    @Column(name = "operational_status", nullable = false, length = 120)
    private String operationalStatus;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
