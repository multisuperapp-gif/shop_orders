package com.msa.shop_orders.consumer.order.dto;

import java.time.LocalDateTime;

public record ConsumerOrderTimelineEventData(
        String oldStatus,
        String newStatus,
        String reason,
        LocalDateTime changedAt
) {
}
