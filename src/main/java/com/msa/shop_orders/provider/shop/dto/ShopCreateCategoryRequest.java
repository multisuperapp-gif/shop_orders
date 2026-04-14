package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.NotBlank;

public record ShopCreateCategoryRequest(
        @NotBlank(message = "name is required")
        String name
) {
}
