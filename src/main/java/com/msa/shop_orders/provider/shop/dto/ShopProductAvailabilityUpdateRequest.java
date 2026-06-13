package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.NotNull;

public record ShopProductAvailabilityUpdateRequest(
        @NotNull(message = "available is required")
        Boolean available
) {
}
