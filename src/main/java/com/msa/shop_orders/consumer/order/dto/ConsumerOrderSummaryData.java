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
        LocalDateTime updatedAt,
        // Seconds left in the 5-minute payment window (only for unpaid
        // ACCEPTED / PAYMENT_PENDING orders; null otherwise). Computed
        // server-side so the apps never restart the countdown.
        Long paymentSecondsRemaining
) {
}
