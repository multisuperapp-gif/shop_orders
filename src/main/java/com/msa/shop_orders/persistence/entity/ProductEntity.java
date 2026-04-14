package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@Setter
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "shop_category_id", nullable = false)
    private Long shopCategoryId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 180)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "short_description", length = 255)
    private String shortDescription;

    @Column(name = "product_type", nullable = false, length = 40)
    private String productType;

    @Column(name = "brand_name", length = 120)
    private String brandName;

    @Column(name = "attributes_json")
    private String attributesJson;

    @Column(name = "requires_prescription", nullable = false)
    private boolean requiresPrescription;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private java.math.BigDecimal avgRating;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews;

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_featured", nullable = false)
    private boolean featured;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
