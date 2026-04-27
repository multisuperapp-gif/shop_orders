package com.msa.shop_orders.consumer.storefront.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.consumer.storefront.dto.StorefrontDtos;
import com.msa.shop_orders.persistence.repository.StorefrontCatalogRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StorefrontCatalogService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final StorefrontCatalogRepository storefrontCatalogRepository;

    public StorefrontCatalogService(StorefrontCatalogRepository storefrontCatalogRepository) {
        this.storefrontCatalogRepository = storefrontCatalogRepository;
    }

    public StorefrontDtos.HomeBootstrapData homeBootstrap(Double latitude, Double longitude, int page, int size) {
        return new StorefrontDtos.HomeBootstrapData(
                findShopTypes(),
                findProducts(null, null, null, latitude, longitude, page, size)
        );
    }

    public List<StorefrontDtos.ShopTypeData> findShopTypes() {
        return storefrontCatalogRepository.findActiveShopTypes().stream()
                .map(this::toShopTypeData)
                .toList();
    }

    public List<StorefrontDtos.ShopCategoryData> findCategories(Long shopTypeId, Long parentCategoryId) {
        return storefrontCatalogRepository.findCategories(shopTypeId, parentCategoryId).stream()
                .map(this::toShopCategoryData)
                .toList();
    }

    public StorefrontDtos.PageResponse<StorefrontDtos.ShopProductCardData> findProducts(
            Long shopTypeId,
            Long categoryId,
            String search,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int limit = safeSize + 1;
        int offset = safePage * safeSize;

        List<StorefrontDtos.ShopProductCardData> rows = storefrontCatalogRepository.findProducts(
                        shopTypeId,
                        categoryId,
                        StringUtils.hasText(search) ? "%" + search.trim() + "%" : null,
                        limit,
                        offset
                ).stream()
                .map(this::toShopProductCardData)
                .toList();
        boolean hasMore = rows.size() > safeSize;
        List<StorefrontDtos.ShopProductCardData> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new StorefrontDtos.PageResponse<>(items, safePage, safeSize, hasMore);
    }

    public StorefrontDtos.ProductDetailData findProductDetail(Long productId, Long variantId) {
        ProductBaseRow product = loadProduct(productId);
        List<StorefrontDtos.ProductImageData> images = loadImages(productId);
        List<StorefrontDtos.ProductVariantData> variants = loadVariants(productId);
        Long selectedVariantId = selectVariantId(variants, variantId);
        List<StorefrontDtos.ProductOptionGroupData> optionGroups = loadOptionGroups(productId);
        return new StorefrontDtos.ProductDetailData(
                product.productId(),
                selectedVariantId,
                product.shopId(),
                product.shopTypeId(),
                product.categoryId(),
                product.productName(),
                product.shopName(),
                product.categoryName(),
                product.brandName(),
                product.description(),
                product.shortDescription(),
                product.productType(),
                product.attributesJson(),
                product.avgRating(),
                product.totalReviews(),
                product.totalOrders(),
                variants.stream().noneMatch(variant -> !variant.outOfStock()),
                images,
                variants,
                optionGroups
        );
    }

    public StorefrontDtos.ShopTypeLandingData landing(
            String normalizedShopType,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        StorefrontDtos.ShopTypeData shopType = requireShopType(normalizedShopType);
        return new StorefrontDtos.ShopTypeLandingData(
                shopType,
                findCategories(shopType.id(), null),
                findProducts(shopType.id(), null, null, latitude, longitude, page, size),
                shops(normalizedShopType, null, latitude, longitude, page, size)
        );
    }

    public List<StorefrontDtos.ShopCategoryData> categories(String normalizedShopType, Long parentCategoryId) {
        StorefrontDtos.ShopTypeData shopType = requireShopType(normalizedShopType);
        return findCategories(shopType.id(), parentCategoryId);
    }

    public StorefrontDtos.PageResponse<StorefrontDtos.ShopProductCardData> products(
            String normalizedShopType,
            Long categoryId,
            String search,
            int page,
            int size
    ) {
        StorefrontDtos.ShopTypeData shopType = requireShopType(normalizedShopType);
        return findProducts(shopType.id(), categoryId, search, null, null, page, size);
    }

    public StorefrontDtos.PageResponse<StorefrontDtos.ShopSummaryData> shops(
            String normalizedShopType,
            String search,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        StorefrontDtos.ShopTypeData shopType = requireShopType(normalizedShopType);
        return findShops(shopType.id(), null, search, latitude, longitude, page, size);
    }

    public StorefrontDtos.ShopProfileData shopProfile(
            String normalizedShopType,
            Long shopId,
            Long categoryId,
            String search,
            int page,
            int size
    ) {
        StorefrontDtos.ShopTypeData shopType = requireShopType(normalizedShopType);
        StorefrontDtos.ShopSummaryData shop = requireShop(shopType.id(), shopId);
        return new StorefrontDtos.ShopProfileData(
                shop,
                findShopCategories(shopId, shop.shopTypeId()),
                findProductsByShop(shopId, categoryId, search, page, size)
        );
    }

    private StorefrontDtos.ShopTypeData requireShopType(String normalizedShopType) {
        return storefrontCatalogRepository.findActiveShopTypeByNormalizedName(normalizedShopType)
                .map(this::toShopTypeData)
                .orElseThrow(() -> new BusinessException("SHOP_TYPE_NOT_FOUND", "Shop type not found", HttpStatus.NOT_FOUND));
    }

    private StorefrontDtos.PageResponse<StorefrontDtos.ShopSummaryData> findShops(
            Long shopTypeId,
            Long categoryId,
            String search,
            Double userLatitude,
            Double userLongitude,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int limit = safeSize + 1;
        int offset = safePage * safeSize;

        List<StorefrontDtos.ShopSummaryData> rows = storefrontCatalogRepository.findShops(
                        shopTypeId,
                        categoryId,
                        StringUtils.hasText(search) ? "%" + search.trim() + "%" : null,
                        userLatitude,
                        userLongitude,
                        limit,
                        offset
                ).stream()
                .map(this::toShopSummaryData)
                .toList();
        boolean hasMore = rows.size() > safeSize;
        List<StorefrontDtos.ShopSummaryData> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new StorefrontDtos.PageResponse<>(items, safePage, safeSize, hasMore);
    }

    private StorefrontDtos.ShopSummaryData requireShop(Long shopTypeId, Long shopId) {
        return storefrontCatalogRepository.findShopSummary(shopTypeId, shopId)
                .map(this::toShopSummaryData)
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Shop not found", HttpStatus.NOT_FOUND));
    }

    private List<StorefrontDtos.ShopCategoryData> findShopCategories(Long shopId, Long shopTypeId) {
        return storefrontCatalogRepository.findShopCategories(shopId, shopTypeId).stream()
                .map(this::toShopCategoryData)
                .toList();
    }

    private StorefrontDtos.PageResponse<StorefrontDtos.ShopProductCardData> findProductsByShop(
            Long shopId,
            Long categoryId,
            String search,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int limit = safeSize + 1;
        int offset = safePage * safeSize;

        List<StorefrontDtos.ShopProductCardData> rows = storefrontCatalogRepository.findProductsByShop(
                        shopId,
                        categoryId,
                        StringUtils.hasText(search) ? "%" + search.trim() + "%" : null,
                        limit,
                        offset
                ).stream()
                .map(this::toShopProductCardData)
                .toList();
        boolean hasMore = rows.size() > safeSize;
        List<StorefrontDtos.ShopProductCardData> items = hasMore ? rows.subList(0, safeSize) : rows;
        return new StorefrontDtos.PageResponse<>(items, safePage, safeSize, hasMore);
    }

    private ProductBaseRow loadProduct(Long productId) {
        return storefrontCatalogRepository.findProductBase(productId)
                .map(row -> new ProductBaseRow(
                        row.getProductId(),
                        row.getShopId(),
                        row.getShopTypeId(),
                        row.getCategoryId(),
                        row.getProductName(),
                        row.getShopName(),
                        row.getCategoryName(),
                        row.getBrandName(),
                        row.getDescription(),
                        row.getShortDescription(),
                        row.getProductType(),
                        row.getAttributesJson(),
                        row.getAvgRating(),
                        row.getTotalReviews() == null ? 0L : row.getTotalReviews(),
                        row.getTotalOrders() == null ? 0L : row.getTotalOrders()
                ))
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));
    }

    private List<StorefrontDtos.ProductImageData> loadImages(Long productId) {
        return storefrontCatalogRepository.findProductImages(productId).stream()
                .map(row -> new StorefrontDtos.ProductImageData(
                        row.getId(),
                        row.getObjectKey(),
                        row.getImageRole(),
                        row.getSortOrder() == null ? 0 : row.getSortOrder(),
                        Boolean.TRUE.equals(row.getPrimary())
                ))
                .toList();
    }

    private List<StorefrontDtos.ProductVariantData> loadVariants(Long productId) {
        return storefrontCatalogRepository.findProductVariants(productId).stream()
                .map(row -> new StorefrontDtos.ProductVariantData(
                        row.getId(),
                        row.getVariantName(),
                        row.getMrp(),
                        row.getSellingPrice(),
                        Boolean.TRUE.equals(row.getDefaultVariant()),
                        Boolean.TRUE.equals(row.getActive()),
                        row.getAttributesJson(),
                        row.getInventoryStatus(),
                        Boolean.TRUE.equals(row.getOutOfStock())
                ))
                .toList();
    }

    private List<StorefrontDtos.ProductOptionGroupData> loadOptionGroups(Long productId) {
        Map<Long, ProductOptionGroupBuilder> grouped = new LinkedHashMap<>();
        for (StorefrontCatalogRepository.ProductOptionRowView row : storefrontCatalogRepository.findProductOptionRows(productId)) {
            long groupId = row.getGroupId();
            ProductOptionGroupBuilder builder = grouped.get(groupId);
            if (builder == null) {
                builder = new ProductOptionGroupBuilder(
                        groupId,
                        row.getGroupName(),
                        row.getGroupType(),
                        row.getMinSelect() == null ? 0 : row.getMinSelect(),
                        row.getMaxSelect() == null ? 0 : row.getMaxSelect(),
                        Boolean.TRUE.equals(row.getRequired())
                );
                grouped.put(groupId, builder);
            }
            if (row.getOptionId() != null) {
                builder.options().add(new StorefrontDtos.ProductOptionData(
                        row.getOptionId(),
                        row.getOptionName(),
                        row.getPriceDelta(),
                        Boolean.TRUE.equals(row.getDefaultOption())
                ));
            }
        }
        return grouped.values().stream().map(ProductOptionGroupBuilder::build).toList();
    }

    private StorefrontDtos.ShopTypeData toShopTypeData(StorefrontCatalogRepository.ShopTypeView row) {
        return new StorefrontDtos.ShopTypeData(
                row.getId(),
                row.getName(),
                row.getNormalizedName(),
                row.getThemeColor(),
                Boolean.TRUE.equals(row.getComingSoon()),
                row.getComingSoonMessage(),
                row.getIconObjectKey(),
                row.getBannerObjectKey(),
                row.getSortOrder() == null ? 0 : row.getSortOrder()
        );
    }

    private StorefrontDtos.ShopCategoryData toShopCategoryData(StorefrontCatalogRepository.ShopCategoryView row) {
        return new StorefrontDtos.ShopCategoryData(
                row.getId(),
                row.getParentCategoryId(),
                row.getShopTypeId(),
                row.getName(),
                row.getNormalizedName(),
                row.getThemeColor(),
                Boolean.TRUE.equals(row.getComingSoon()),
                row.getComingSoonMessage(),
                row.getImageObjectKey(),
                row.getSortOrder() == null ? 0 : row.getSortOrder()
        );
    }

    private StorefrontDtos.ShopProductCardData toShopProductCardData(StorefrontCatalogRepository.ProductCardView row) {
        return new StorefrontDtos.ShopProductCardData(
                row.getProductId(),
                row.getVariantId(),
                row.getShopId(),
                row.getShopTypeId(),
                row.getCategoryId(),
                row.getProductName(),
                row.getShopName(),
                row.getCategoryName(),
                row.getBrandName(),
                row.getShortDescription(),
                row.getProductType(),
                row.getMrp(),
                row.getSellingPrice(),
                row.getAvgRating(),
                row.getTotalReviews() == null ? 0L : row.getTotalReviews(),
                row.getTotalOrders() == null ? 0L : row.getTotalOrders(),
                row.getInventoryStatus(),
                Boolean.TRUE.equals(row.getOutOfStock()),
                row.getPromotionScore() == null ? 0 : row.getPromotionScore(),
                row.getImageObjectKey()
        );
    }

    private StorefrontDtos.ShopSummaryData toShopSummaryData(StorefrontCatalogRepository.ShopSummaryView row) {
        return new StorefrontDtos.ShopSummaryData(
                row.getShopId(),
                row.getShopTypeId(),
                row.getShopName(),
                row.getShopCode(),
                row.getLogoObjectKey(),
                row.getCoverObjectKey(),
                row.getAvgRating(),
                row.getTotalReviews() == null ? 0L : row.getTotalReviews(),
                row.getCity(),
                row.getLatitude(),
                row.getLongitude(),
                row.getDeliveryType(),
                row.getRadiusKm(),
                row.getMinOrderAmount(),
                row.getDeliveryFee(),
                Boolean.TRUE.equals(row.getOpenNow()),
                Boolean.TRUE.equals(row.getClosingSoon()),
                Boolean.TRUE.equals(row.getAcceptsOrders()),
                row.getClosesAt()
        );
    }

    private static Long selectVariantId(List<StorefrontDtos.ProductVariantData> variants, Long requestedVariantId) {
        if (variants.isEmpty()) {
            return null;
        }
        if (requestedVariantId != null && variants.stream().anyMatch(variant -> requestedVariantId.equals(variant.id()))) {
            return requestedVariantId;
        }
        return variants.stream()
                .filter(StorefrontDtos.ProductVariantData::defaultVariant)
                .map(StorefrontDtos.ProductVariantData::id)
                .findFirst()
                .orElseGet(() -> variants.getFirst().id());
    }

    private record ProductBaseRow(
            Long productId,
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
            long totalOrders
    ) {
    }

    private record ProductOptionGroupBuilder(
            Long id,
            String groupName,
            String groupType,
            int minSelect,
            int maxSelect,
            boolean required,
            List<StorefrontDtos.ProductOptionData> options
    ) {
        private ProductOptionGroupBuilder(
                Long id,
                String groupName,
                String groupType,
                int minSelect,
                int maxSelect,
                boolean required
        ) {
            this(id, groupName, groupType, minSelect, maxSelect, required, new ArrayList<>());
        }

        private StorefrontDtos.ProductOptionGroupData build() {
            return new StorefrontDtos.ProductOptionGroupData(
                    id,
                    groupName,
                    groupType,
                    minSelect,
                    maxSelect,
                    required,
                    options
            );
        }
    }
}
