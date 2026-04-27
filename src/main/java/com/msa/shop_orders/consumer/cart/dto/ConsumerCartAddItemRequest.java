package com.msa.shop_orders.consumer.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ConsumerCartAddItemRequest(
        @NotNull Long productId,
        Long variantId,
        @NotNull @Min(1) Integer quantity,
        List<Long> optionIds,
        String cookingRequest
) {
}
