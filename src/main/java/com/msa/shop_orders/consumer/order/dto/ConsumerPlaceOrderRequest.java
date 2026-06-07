package com.msa.shop_orders.consumer.order.dto;

import jakarta.validation.constraints.NotNull;

public record ConsumerPlaceOrderRequest(
        @NotNull Long addressId,
        String fulfillmentType,
        // Recipient name shown to the shop on the order card (defaults to the
        // customer's profile name; may differ if delivering to someone else).
        String recipientName
) {
}
