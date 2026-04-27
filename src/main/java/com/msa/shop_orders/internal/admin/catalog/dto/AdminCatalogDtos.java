package com.msa.shop_orders.internal.admin.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AdminCatalogDtos {
    private AdminCatalogDtos() {
    }

    public record ShopSummaryData(
            Long shopId,
            String shopCode,
            String shopName,
            String shopType,
            String approvalStatus,
            String operationalStatus,
            String ownerPublicUserId,
            String ownerPhone,
            String ownerEmail
    ) {
    }

    public record ShopDetailData(
            Long shopId,
            String shopCode,
            String shopName,
            String shopType,
            String approvalStatus,
            String operationalStatus,
            String ownerPublicUserId,
            String ownerPhone,
            String ownerEmail,
            List<ProductSummaryData> products
    ) {
    }

    public record ProductSummaryData(
            Long productId,
            Long shopId,
            Long categoryId,
            String sku,
            String productName,
            String shopName,
            String categoryName,
            String productType,
            boolean active,
            boolean requiresPrescription
    ) {
    }

    public record ProductCategorySummaryData(
            Long categoryId,
            String categoryName,
            String shopType,
            boolean active,
            long itemCount
    ) {
    }

    public record ShopOperationalStatusUpdateRequest(
            String operationalStatus
    ) {
    }

    public record ProductActiveUpdateRequest(
            boolean active
    ) {
    }

    public record ProductFeaturedUpdateRequest(
            boolean featured
    ) {
    }

    public record ProductPromotionStatusUpdateRequest(
            String status
    ) {
    }

    public record ProductCouponActiveUpdateRequest(
            boolean active
    ) {
    }

    public record ProductDetailData(
            Long productId,
            String sku,
            String productName,
            String shortDescription,
            String description,
            String productType,
            String brandName,
            boolean active,
            boolean featured,
            boolean requiresPrescription,
            BigDecimal avgRating,
            Integer totalReviews,
            Integer totalOrders,
            Map<String, Object> attributes,
            Long shopId,
            String shopName,
            Long categoryId,
            String categoryName,
            ProductPromotionData promotion,
            ProductCouponData coupon,
            ShopDeliveryRuleData deliveryRule,
            List<ProductImageData> images,
            List<ProductVariantData> variants
    ) {
    }

    public record ProductPromotionData(
            Long id,
            String promotionType,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Integer priorityScore,
            BigDecimal paidAmount,
            String status
    ) {
    }

    public record ProductCouponData(
            Long id,
            String couponCode,
            String couponTitle,
            String discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            boolean active
    ) {
    }

    public record ShopDeliveryRuleData(
            Long shopLocationId,
            String deliveryType,
            BigDecimal radiusKm,
            BigDecimal minOrderAmount,
            BigDecimal deliveryFee,
            BigDecimal freeDeliveryAbove,
            Integer orderCutoffMinutesBeforeClose,
            Integer closingSoonMinutes
    ) {
    }

    public record ProductImageData(
            Long imageId,
            Long fileId,
            String imageRole,
            Long variantId,
            Integer sortOrder,
            boolean primaryImage
    ) {
    }

    public record ProductVariantData(
            Long variantId,
            String variantName,
            String colorName,
            String colorHex,
            BigDecimal unitValue,
            String unitType,
            BigDecimal weightInGrams,
            BigDecimal mrp,
            BigDecimal sellingPrice,
            boolean defaultVariant,
            boolean active,
            Integer sortOrder,
            Integer quantityAvailable,
            Integer reservedQuantity,
            String inventoryStatus,
            Map<String, Object> attributes
    ) {
    }
}
