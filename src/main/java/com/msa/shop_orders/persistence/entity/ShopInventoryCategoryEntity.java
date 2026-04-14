package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "shop_inventory_categories")
@Getter
@Setter
public class ShopInventoryCategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "shop_category_id", nullable = false)
    private Long shopCategoryId;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;
}
