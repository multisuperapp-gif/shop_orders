package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShopProductCouponRequest(
        Boolean enabled,
        String couponCode,
        String couponTitle,
        String discountType,
        @PositiveOrZero(message = "discount_value must be zero or positive")
        BigDecimal discountValue,
        @PositiveOrZero(message = "min_order_amount must be zero or positive")
        BigDecimal minOrderAmount,
        @PositiveOrZero(message = "max_discount_amount must be zero or positive")
        BigDecimal maxDiscountAmount,
        LocalDateTime startsAt,
        LocalDateTime endsAt
) {
}
