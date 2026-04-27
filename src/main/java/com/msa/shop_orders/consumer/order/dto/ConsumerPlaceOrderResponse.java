package com.msa.shop_orders.consumer.order.dto;

import java.math.BigDecimal;

public record ConsumerPlaceOrderResponse(
        Long orderId,
        String orderCode,
        String orderStatus,
        String paymentStatus,
        Long paymentId,
        String paymentCode,
        BigDecimal totalAmount,
        String currencyCode,
        String nextAction
) {
}
