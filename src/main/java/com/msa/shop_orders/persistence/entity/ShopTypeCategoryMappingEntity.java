package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "shop_type_category_mappings")
@Getter
@Setter
public class ShopTypeCategoryMappingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_type_id", nullable = false)
    private Long shopTypeId;

    @Column(name = "shop_category_id", nullable = false)
    private Long shopCategoryId;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
