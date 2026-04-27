package com.msa.shop_orders.consumer.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ConsumerOrderSummaryData(
        Long orderId,
        String orderCode,
        Long shopId,
        String shopName,
        String primaryItemName,
        Long primaryImageFileId,
        Integer itemCount,
        String orderStatus,
        String paymentStatus,
        BigDecimal totalAmount,
        String currencyCode,
        boolean cancellable,
        boolean refundPresent,
        String latestRefundStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
