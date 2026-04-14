package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ShopProductVariantData(
        Long variantId,
        String variantName,
        String colorName,
        String colorHex,
        BigDecimal unitValue,
        String unitType,
        BigDecimal weightInGrams,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        Integer quantityAvailable,
        Integer reservedQuantity,
        Integer reorderLevel,
        String inventoryStatus,
        boolean defaultVariant,
        boolean active,
        Integer sortOrder,
        Map<String, Object> attributes
) {
}
