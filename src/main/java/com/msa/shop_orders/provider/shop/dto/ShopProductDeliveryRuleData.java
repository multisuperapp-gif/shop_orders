package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;

public record ShopProductDeliveryRuleData(
        Long shopLocationId,
        String deliveryType,
        BigDecimal radiusKm,
        BigDecimal minOrderAmount,
        BigDecimal deliveryFee,
        BigDecimal freeDeliveryAbove,
        Integer orderCutoffMinutesBeforeClose,
        Integer closingSoonMinutes
) {
}
