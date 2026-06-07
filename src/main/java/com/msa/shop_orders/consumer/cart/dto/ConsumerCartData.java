package com.msa.shop_orders.consumer.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record ConsumerCartData(
        Long userId,
        Long shopId,
        String shopName,
        String currencyCode,
        String cartContext,
        int itemCount,
        BigDecimal subtotal,
        List<ConsumerCartItemData> items,
        // Auto-applicable shop coupon (null when none is currently available).
        String couponCode,
        String couponTitle,
        BigDecimal couponMinOrderAmount,
        // Discount for the current subtotal (0 when the coupon isn't eligible yet).
        BigDecimal couponDiscountAmount
) {
}
