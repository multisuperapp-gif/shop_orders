package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.NotBlank;

public record ShopOrderStatusUpdateRequest(
        @NotBlank(message = "new status is required")
        String newStatus,
        String reason,
        // Delivery completion OTP the customer reads out; required to mark an
        // order DELIVERED (the shop types it in to confirm hand-off).
        String completionOtp
) {
}
