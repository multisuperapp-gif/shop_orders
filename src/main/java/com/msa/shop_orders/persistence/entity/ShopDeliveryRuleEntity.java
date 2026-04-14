package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "shop_delivery_rules")
@Getter
@Setter
public class ShopDeliveryRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_location_id", nullable = false)
    private Long shopLocationId;

    @Column(name = "delivery_type", nullable = false, length = 20)
    private String deliveryType;

    @Column(name = "radius_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal radiusKm;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "delivery_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "free_delivery_above", precision = 12, scale = 2)
    private BigDecimal freeDeliveryAbove;

    @Column(name = "order_cutoff_minutes_before_close", nullable = false)
    private Integer orderCutoffMinutesBeforeClose;

    @Column(name = "closing_soon_minutes", nullable = false)
    private Integer closingSoonMinutes;
}
