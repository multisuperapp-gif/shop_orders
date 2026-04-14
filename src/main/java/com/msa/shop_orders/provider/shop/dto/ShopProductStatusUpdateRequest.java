package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.NotNull;

public record ShopProductStatusUpdateRequest(
        @NotNull(message = "active is required")
        Boolean active
) {
}
