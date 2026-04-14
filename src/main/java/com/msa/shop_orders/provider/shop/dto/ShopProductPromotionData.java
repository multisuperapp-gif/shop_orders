package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShopProductPromotionData(
        Long promotionId,
        String promotionType,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Integer priorityScore,
        BigDecimal paidAmount,
        String status
) {
}
