package com.msa.shop_orders.consumer.order.dto;

import jakarta.validation.constraints.NotNull;

public record ConsumerPlaceOrderRequest(
        @NotNull Long addressId,
        String fulfillmentType
) {
}
