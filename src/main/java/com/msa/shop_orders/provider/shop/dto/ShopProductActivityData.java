package com.msa.shop_orders.provider.shop.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ShopProductActivityData(
        String activityId,
        Long shopId,
        Long productId,
        Long actorUserId,
        String actorPublicUserId,
        String eventType,
        String productName,
        Map<String, Object> details,
        LocalDateTime createdAt
) {
}
