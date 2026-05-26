package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ShopCategoryOrderUpdateRequest(
        @NotEmpty(message = "categoryIds is required")
        List<Long> categoryIds
) {
}
