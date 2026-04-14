package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ShopProductData(
        Long productId,
        Long categoryId,
        String categoryName,
        String itemName,
        String shortDescription,
        String description,
        String brandName,
        String variantName,
        BigDecimal unitValue,
        String unitType,
        BigDecimal weightInGrams,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        Integer quantityAvailable,
        Integer reservedQuantity,
        Integer reorderLevel,
        String inventoryStatus,
        Long imageFileId,
        boolean active,
        boolean featured,
        Map<String, Object> attributes,
        BigDecimal avgRating,
        Integer totalReviews,
        Integer totalOrders,
        ShopProductPromotionData promotion,
        ShopProductCouponData coupon,
        ShopProductDeliveryRuleData deliveryRule,
        List<ShopProductVariantData> variants,
        List<ShopProductImageData> images,
        LocalDateTime updatedAt
) {
}
