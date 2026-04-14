package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ShopCreateProductRequest(
        @NotNull(message = "category_id is required")
        Long categoryId,
        @NotBlank(message = "item_name is required")
        String itemName,
        String shortDescription,
        String description,
        String brandName,
        String productType,
        Boolean requiresPrescription,
        String variantName,
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
        Long imageFileId,
        String sku,
        Boolean active,
        Boolean featured,
        Map<String, Object> attributes,
        @Valid
        List<ShopProductVariantRequest> variants,
        @Valid
        List<ShopProductImageRequest> images,
        @Valid
        ShopProductPromotionRequest promotion,
        @Valid
        ShopProductCouponRequest coupon
) {
}
