package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.NotNull;

public record ShopCategoryStatusUpdateRequest(
        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}
