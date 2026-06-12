package com.msa.shop_orders.consumer.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ConsumerOrderDetailData(
        Long orderId,
        String orderCode,
        Long shopId,
        String shopName,
        String orderStatus,
        String paymentStatus,
        String paymentCode,
        String fulfillmentType,
        String addressLabel,
        String addressLine,
        BigDecimal subtotalAmount,
        BigDecimal taxAmount,
        BigDecimal deliveryFeeAmount,
        BigDecimal platformFeeAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String currencyCode,
        boolean cancellable,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ConsumerOrderItemData> items,
        List<ConsumerOrderTimelineEventData> timeline,
        ConsumerRefundSummaryData refund,
        String cancelledBy,
        // Delivery destination (the customer's address) for the tracking map.
        BigDecimal deliveryLatitude,
        BigDecimal deliveryLongitude,
        // Live delivery-agent position while OUT_FOR_DELIVERY (null otherwise).
        BigDecimal deliveryAgentLatitude,
        BigDecimal deliveryAgentLongitude,
        LocalDateTime deliveryAgentLocationAt,
        // Seconds left in the 5-minute payment window (only for unpaid
        // ACCEPTED / PAYMENT_PENDING orders; null otherwise).
        Long paymentSecondsRemaining
) {
}
