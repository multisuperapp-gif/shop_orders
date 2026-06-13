package com.msa.shop_orders.consumer.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ConsumerOrderReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        String comment
) {
}
