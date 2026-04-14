package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
public class ProductVariantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_name", nullable = false, length = 120)
    private String variantName;

    @Column(name = "attributes_json")
    private String attributesJson;

    @Column(name = "unit_value", precision = 10, scale = 2)
    private BigDecimal unitValue;

    @Column(name = "unit_type", length = 30)
    private String unitType;

    @Column(name = "weight_in_grams", precision = 10, scale = 2)
    private BigDecimal weightInGrams;

    @Column(name = "mrp", nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "is_default", nullable = false)
    private boolean defaultVariant;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
