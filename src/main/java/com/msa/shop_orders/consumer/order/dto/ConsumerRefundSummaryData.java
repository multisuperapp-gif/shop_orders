package com.msa.shop_orders.consumer.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ConsumerRefundSummaryData(
        String refundCode,
        String refundStatus,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        String reason,
        LocalDateTime initiatedAt,
        LocalDateTime completedAt
) {
}
