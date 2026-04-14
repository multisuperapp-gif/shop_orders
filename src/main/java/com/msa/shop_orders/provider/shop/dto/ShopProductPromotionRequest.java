package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShopProductPromotionRequest(
        Boolean enabled,
        String promotionType,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Integer priorityScore,
        @PositiveOrZero(message = "paid_amount must be zero or positive")
        BigDecimal paidAmount,
        String status
) {
}
