package com.msa.shop_orders.consumer.checkout.dto;

public record ConsumerCheckoutPreviewRequest(
        Long addressId,
        String fulfillmentType
) {
}
