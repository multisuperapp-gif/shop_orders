package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_images")
@Getter
@Setter
public class ProductImageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "image_role", nullable = false, length = 20)
    private String imageRole;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryImage;
}
