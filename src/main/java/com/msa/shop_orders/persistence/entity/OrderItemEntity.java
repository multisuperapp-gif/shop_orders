package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "selected_options_json")
    private String selectedOptionsJson;

    @Column(name = "product_name_snapshot", nullable = false, length = 180)
    private String productNameSnapshot;

    @Column(name = "variant_name_snapshot", length = 120)
    private String variantNameSnapshot;

    @Column(name = "image_file_id_snapshot")
    private Long imageFileIdSnapshot;

    @Column(name = "shop_name_snapshot", nullable = false, length = 180)
    private String shopNameSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(name = "tax_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxSnapshot;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;
}
