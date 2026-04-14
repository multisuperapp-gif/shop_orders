package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Map;

public record ShopProductVariantRequest(
        Long variantId,
        String clientKey,
        String variantName,
        String colorName,
        String colorHex,
        BigDecimal unitValue,
        String unitType,
        @PositiveOrZero(message = "weight_in_grams must be zero or positive")
        BigDecimal weightInGrams,
        @PositiveOrZero(message = "mrp must be zero or positive")
        BigDecimal mrp,
        @PositiveOrZero(message = "selling_price must be zero or positive")
        BigDecimal sellingPrice,
        @PositiveOrZero(message = "quantity_available must be zero or positive")
        Integer quantityAvailable,
        @PositiveOrZero(message = "reorder_level must be zero or positive")
        Integer reorderLevel,
        Boolean defaultVariant,
        Boolean active,
        Integer sortOrder,
        Map<String, Object> attributes
) {
}
