package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.NotBlank;

public record ShopOrderStatusUpdateRequest(
        @NotBlank(message = "new status is required")
        String newStatus,
        String reason
) {
}
