package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ShopProductImageRequest(
        @NotNull(message = "file_id is required")
        Long fileId,
        String imageRole,
        String variantClientKey,
        @PositiveOrZero(message = "sort_order must be zero or positive")
        Integer sortOrder,
        Boolean primaryImage
) {
}
