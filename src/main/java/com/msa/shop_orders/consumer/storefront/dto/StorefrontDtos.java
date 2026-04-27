package com.msa.shop_orders.consumer.storefront.dto;

import java.math.BigDecimal;
import java.util.List;

public final class StorefrontDtos {
    private StorefrontDtos() {
    }

    public record HomeBootstrapData(
            List<ShopTypeData> shopTypes,
            PageResponse<ShopProductCardData> featuredProducts
    ) {
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            boolean hasMore
    ) {
    }

    public record ProductDetailData(
            Long productId,
            Long selectedVariantId,
            Long shopId,
            Long shopTypeId,
            Long categoryId,
            String productName,
            String shopName,
            String categoryName,
            String brandName,
            String description,
            String shortDescription,
            String productType,
            String attributesJson,
            BigDecimal avgRating,
            long totalReviews,
            long totalOrders,
            boolean outOfStock,
            List<ProductImageData> images,
            List<ProductVariantData> variants,
            List<ProductOptionGroupData> optionGroups
    ) {
    }

    public record ProductImageData(
            Long id,
            String objectKey,
            String imageRole,
            int sortOrder,
            boolean primaryImage
    ) {
    }

    public record ProductOptionGroupData(
            Long id,
            String groupName,
            String groupType,
            int minSelect,
            int maxSelect,
            boolean required,
            List<ProductOptionData> options
    ) {
    }

    public record ProductOptionData(
            Long id,
            String optionName,
            BigDecimal priceDelta,
            boolean defaultOption
    ) {
    }

    public record ProductVariantData(
            Long id,
            String variantName,
            BigDecimal mrp,
            BigDecimal sellingPrice,
            boolean defaultVariant,
            boolean active,
            String attributesJson,
            String inventoryStatus,
            boolean outOfStock
    ) {
    }

    public record ShopCategoryData(
            Long id,
            Long parentCategoryId,
            Long shopTypeId,
            String name,
            String normalizedName,
            String themeColor,
            boolean comingSoon,
            String comingSoonMessage,
            String imageObjectKey,
            int sortOrder
    ) {
    }

    public record ShopProductCardData(
            Long productId,
            Long variantId,
            Long shopId,
            Long shopTypeId,
            Long categoryId,
            String productName,
            String shopName,
            String categoryName,
            String brandName,
            String shortDescription,
            String productType,
            BigDecimal mrp,
            BigDecimal sellingPrice,
            BigDecimal avgRating,
            long totalReviews,
            long totalOrders,
            String inventoryStatus,
            boolean outOfStock,
            int promotionScore,
            String imageObjectKey
    ) {
    }

    public record ShopProfileData(
            ShopSummaryData shop,
            List<ShopCategoryData> categories,
            PageResponse<ShopProductCardData> products
    ) {
    }

    public record ShopSummaryData(
            Long shopId,
            Long shopTypeId,
            String shopName,
            String shopCode,
            String logoObjectKey,
            String coverObjectKey,
            BigDecimal avgRating,
            long totalReviews,
            String city,
            BigDecimal latitude,
            BigDecimal longitude,
            String deliveryType,
            BigDecimal deliveryRadiusKm,
            BigDecimal minOrderAmount,
            BigDecimal deliveryFee,
            boolean openNow,
            boolean closingSoon,
            boolean acceptsOrders,
            String closesAt
    ) {
    }

    public record ShopTypeLandingData(
            ShopTypeData shopType,
            List<ShopCategoryData> categories,
            PageResponse<ShopProductCardData> products,
            PageResponse<ShopSummaryData> shops
    ) {
    }

    public record ShopTypeData(
            Long id,
            String name,
            String normalizedName,
            String themeColor,
            boolean comingSoon,
            String comingSoonMessage,
            String iconObjectKey,
            String bannerObjectKey,
            int sortOrder
    ) {
    }
}
