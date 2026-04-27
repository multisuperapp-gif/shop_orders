package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_option_groups")
@Getter
@Setter
public class ProductOptionGroupEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "group_name", nullable = false, length = 120)
    private String groupName;

    @Column(name = "group_type", nullable = false, length = 20)
    private String groupType;

    @Column(name = "min_select", nullable = false)
    private Integer minSelect;

    @Column(name = "max_select", nullable = false)
    private Integer maxSelect;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
