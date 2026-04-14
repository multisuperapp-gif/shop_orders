package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShopProductCouponData(
        Long couponId,
        String couponCode,
        String couponTitle,
        String discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean active
) {
}
