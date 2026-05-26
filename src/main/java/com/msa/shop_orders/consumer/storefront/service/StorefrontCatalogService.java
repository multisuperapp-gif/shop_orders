package com.msa.shop_orders.consumer.storefront.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.RestaurantItemVisibilityPolicy;
import com.msa.shop_orders.consumer.storefront.dto.StorefrontDtos;
import com.msa.shop_orders.persistence.repository.StorefrontCatalogRepository;
import com.msa.shop_orders.provider.shop.service.ShopCategoryViewService;
import com.msa.shop_orders.provider.shop.service.ShopShellViewService;
import com.msa.shop_orders.provider.shop.view.ShopProductView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StorefrontCatalogService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 20;

    private final StorefrontCatalogRepository storefrontCatalogRepository;
    private final ShopCategoryViewService shopCategoryViewService;
    private final ShopProductViewRepository shopProductViewRepository;
    private final ShopShellViewRepository shopShellViewRepository;
    private final ShopShellViewService shopShellViewService;
    private final ObjectMapper objectMapper;

    public StorefrontCatalogService(
            StorefrontCatalogRepository storefrontCatalogRepository,
            ShopCategoryViewService shopCategoryViewService,
            ShopProductViewRepository shopProductViewRepository,
            ShopShellViewRepository shopShellViewRepository,
            ShopShellViewService shopShellViewService,
            ObjectMapper objectMapper
    ) {
        this.storefrontCatalogRepository = storefrontCatalogRepository;
        this.shopCategoryViewService = shopCategoryViewService;
        this.shopProductViewRepository = shopProductViewRepository;
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopShellViewService = shopShellViewService;
        this.objectMapper = objectMapper;
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
        return resolveCategories(shopTypeId, parentCategoryId).stream()
                .map(this::toShopCategoryDataFromMongo)
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
        List<StorefrontDtos.ShopProductCardData> rows = loadVisibleProducts(shopTypeId, categoryId, search, null).stream()
                .map(result -> toShopProductCardData(result.product(), result.shop()))
                .toList();
        return page(rows, safePage, safeSize);
    }

    public StorefrontDtos.ProductDetailData findProductDetail(Long productId, Long variantId) {
        ShopProductView product = shopProductViewRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));
        ShopShellView shop = loadVisibleShop(product.getShopId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND));
        if (!isActiveProduct(product) || !isVisibleProductForShop(shop, product)) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found", HttpStatus.NOT_FOUND);
        }
        List<StorefrontDtos.ProductImageData> images = loadImages(product);
        List<StorefrontDtos.ProductVariantData> variants = loadVariants(product);
        Long selectedVariantId = selectVariantId(variants, variantId);
        List<StorefrontDtos.ProductOptionGroupData> optionGroups = List.of();
        return new StorefrontDtos.ProductDetailData(
                product.getProductId(),
                selectedVariantId,
                product.getShopId(),
                shop.getShopTypeId(),
                product.getCategoryId(),
                defaultString(product.getItemName(), "Product"),
                defaultString(shop.getShopName(), "Shop"),
                product.getCategoryName(),
                product.getBrandName(),
                product.getDescription(),
                product.getShortDescription(),
                product.getProductType(),
                toJson(product.getAttributes()),
                safeBigDecimal(product.getAvgRating()),
                safeLong(product.getTotalReviews()),
                safeLong(product.getTotalOrders()),
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
        return shopCategoryViewService.findEnabledShopCategories(shopId, shopTypeId).stream()
                .map(this::toShopCategoryDataFromMongo)
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
        List<StorefrontDtos.ShopProductCardData> rows = loadVisibleProducts(null, categoryId, search, shopId).stream()
                .map(result -> toShopProductCardData(result.product(), result.shop()))
                .toList();
        return page(rows, safePage, safeSize);
    }

    private StorefrontDtos.ShopTypeData toShopTypeData(StorefrontCatalogRepository.ShopTypeView row) {
        return new StorefrontDtos.ShopTypeData(
                row.getId(),
                row.getName(),
                row.getNormalizedName(),
                row.getThemeColor(),
                isTrueFlag(row.getComingSoon()),
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
                isTrueFlag(row.getComingSoon()),
                row.getComingSoonMessage(),
                row.getImageObjectKey(),
                row.getSortOrder() == null ? 0 : row.getSortOrder()
        );
    }

    private StorefrontDtos.ShopCategoryData toShopCategoryDataFromMongo(com.msa.shop_orders.provider.shop.view.ShopCategoryView row) {
        return new StorefrontDtos.ShopCategoryData(
                row.getCategoryId(),
                row.getParentCategoryId(),
                row.getShopTypeId(),
                row.getName(),
                row.getNormalizedName(),
                row.getThemeColor(),
                row.isComingSoon(),
                row.getComingSoonMessage(),
                row.getImageObjectKey(),
                row.getSortOrder()
        );
    }

    private List<com.msa.shop_orders.provider.shop.view.ShopCategoryView> resolveCategories(Long shopTypeId, Long parentCategoryId) {
        if (parentCategoryId != null) {
            return List.of();
        }
        if (shopTypeId == null) {
            return storefrontCatalogRepository.findActiveShopTypes().stream()
                    .map(StorefrontCatalogRepository.ShopTypeView::getId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .flatMap(typeId -> shopCategoryViewService.findAllowedTypeCategories(typeId).stream())
                    .toList();
        }
        return shopCategoryViewService.findAllowedTypeCategories(shopTypeId);
    }

    private boolean isTrueFlag(Number flag) {
        return flag != null && flag.intValue() != 0;
    }

    private List<ProductWithShop> loadVisibleProducts(Long shopTypeId, Long categoryId, String search, Long shopId) {
        Map<Long, ShopShellView> shops = loadVisibleShops(shopTypeId, shopId);
        if (shops.isEmpty()) {
            return List.of();
        }
        String normalizedSearch = normalizeSearch(search);
        return shopProductViewRepository.findAll().stream()
                .filter(this::isActiveProduct)
                .filter(product -> shopId == null || Objects.equals(product.getShopId(), shopId))
                .filter(product -> categoryId == null || Objects.equals(product.getCategoryId(), categoryId))
                .filter(product -> shops.containsKey(product.getShopId()))
                .filter(product -> isVisibleProductForShop(shops.get(product.getShopId()), product))
                .filter(product -> matchesSearch(product, shops.get(product.getShopId()), normalizedSearch))
                .sorted(productComparator())
                .map(product -> new ProductWithShop(product, shops.get(product.getShopId())))
                .toList();
    }

    private Map<Long, ShopShellView> loadVisibleShops(Long shopTypeId, Long shopId) {
        List<ShopShellView> shells;
        if (shopId != null) {
            shells = shopShellViewRepository.findById(shopId).stream().toList();
        } else if (shopTypeId != null) {
            shells = shopShellViewRepository.findByApprovalStatusAndShopTypeId("APPROVED", shopTypeId);
        } else {
            shells = shopShellViewRepository.findByApprovalStatus("APPROVED");
        }
        Map<Long, ShopShellView> visible = new LinkedHashMap<>();
        for (ShopShellView shell : shells) {
            if (shell == null || shell.getShopId() == null) {
                continue;
            }
            ShopShellView latestShell = shopShellViewService.findByShopId(shell.getShopId()).orElse(shell);
            if (!isVisibleShop(latestShell)) {
                continue;
            }
            visible.put(latestShell.getShopId(), latestShell);
        }
        return visible;
    }

    private Optional<ShopShellView> loadVisibleShop(Long shopId) {
        return shopShellViewRepository.findById(shopId).filter(this::isVisibleShop);
    }

    private boolean isVisibleShop(ShopShellView shell) {
        if (shell == null) {
            return false;
        }
        return "APPROVED".equalsIgnoreCase(shell.getApprovalStatus())
                && !"INACTIVE".equalsIgnoreCase(shell.getOperationalStatus());
    }

    private boolean isActiveProduct(ShopProductView product) {
        return product != null && product.isActive();
    }

    private boolean isVisibleProductForShop(ShopShellView shop, ShopProductView product) {
        return shop != null
                && product != null
                && RestaurantItemVisibilityPolicy.isCompatible(shop.getRestaurantServiceType(), product.getAttributes());
    }

    private String normalizeSearch(String search) {
        return StringUtils.hasText(search) ? search.trim().toLowerCase() : null;
    }

    private boolean matchesSearch(ShopProductView product, ShopShellView shop, String normalizedSearch) {
        if (normalizedSearch == null) {
            return true;
        }
        return containsIgnoreCase(product.getItemName(), normalizedSearch)
                || containsIgnoreCase(shop == null ? null : shop.getShopName(), normalizedSearch)
                || containsIgnoreCase(product.getCategoryName(), normalizedSearch)
                || containsIgnoreCase(product.getBrandName(), normalizedSearch);
    }

    private boolean containsIgnoreCase(String value, String normalizedSearch) {
        return value != null && value.toLowerCase().contains(normalizedSearch);
    }

    private Comparator<ShopProductView> productComparator() {
        return Comparator
                .comparing(this::resolveOutOfStock)
                .thenComparing((ShopProductView product) -> promotionScore(product), Comparator.reverseOrder())
                .thenComparing((ShopProductView product) -> product.isFeatured() ? 1 : 0, Comparator.reverseOrder())
                .thenComparing((ShopProductView product) -> safeBigDecimal(product.getAvgRating()), Comparator.reverseOrder())
                .thenComparing((ShopProductView product) -> safeInteger(product.getTotalOrders()), Comparator.reverseOrder())
                .thenComparing(ShopProductView::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private <T> StorefrontDtos.PageResponse<T> page(List<T> rows, int page, int size) {
        int fromIndex = Math.min(page * size, rows.size());
        int toIndex = Math.min(fromIndex + size, rows.size());
        boolean hasMore = toIndex < rows.size();
        return new StorefrontDtos.PageResponse<>(rows.subList(fromIndex, toIndex), page, size, hasMore);
    }

    private StorefrontDtos.ShopProductCardData toShopProductCardData(ShopProductView product, ShopShellView shop) {
        ShopProductView.Variant variant = resolvePrimaryVariant(product);
        return new StorefrontDtos.ShopProductCardData(
                product.getProductId(),
                variant == null ? null : variant.getVariantId(),
                product.getShopId(),
                shop == null ? null : shop.getShopTypeId(),
                product.getCategoryId(),
                defaultString(product.getItemName(), "Product"),
                defaultString(shop == null ? null : shop.getShopName(), "Shop"),
                product.getCategoryName(),
                product.getBrandName(),
                product.getShortDescription(),
                product.getProductType(),
                variant == null ? product.getMrp() : safeBigDecimal(variant.getMrp()),
                variant == null ? product.getSellingPrice() : safeBigDecimal(variant.getSellingPrice()),
                safeBigDecimal(product.getAvgRating()),
                safeLong(product.getTotalReviews()),
                safeLong(product.getTotalOrders()),
                resolveInventoryStatus(product),
                resolveOutOfStock(product),
                promotionScore(product),
                null,
                toJson(product.getAttributes()),
                attributeValue(product.getAttributes(), "foodPreference")
        );
    }

    private List<StorefrontDtos.ProductImageData> loadImages(ShopProductView product) {
        return Optional.ofNullable(product.getImages()).orElse(List.of()).stream()
                .sorted(Comparator.comparing(ShopProductView.Image::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(image -> new StorefrontDtos.ProductImageData(
                        image.getImageId(),
                        null,
                        image.getImageRole(),
                        image.getSortOrder() == null ? 0 : image.getSortOrder(),
                        image.isPrimaryImage()
                ))
                .toList();
    }

    private List<StorefrontDtos.ProductVariantData> loadVariants(ShopProductView product) {
        List<ShopProductView.Variant> variants = Optional.ofNullable(product.getVariants()).orElse(List.of());
        if (variants.isEmpty()) {
            return List.of(new StorefrontDtos.ProductVariantData(
                    product.getProductId(),
                    defaultString(product.getVariantName(), product.getItemName()),
                    product.getMrp(),
                    product.getSellingPrice(),
                    true,
                    product.isActive(),
                    toJson(product.getAttributes()),
                    resolveInventoryStatus(product),
                    resolveOutOfStock(product)
            ));
        }
        return variants.stream()
                .sorted(Comparator.comparing(ShopProductView.Variant::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(variant -> new StorefrontDtos.ProductVariantData(
                        variant.getVariantId(),
                        variant.getVariantName(),
                        safeBigDecimal(variant.getMrp()),
                        safeBigDecimal(variant.getSellingPrice()),
                        variant.isDefaultVariant(),
                        variant.isActive(),
                        toJson(variant.getAttributes()),
                        defaultString(variant.getInventoryStatus(), "OUT_OF_STOCK"),
                        isVariantOutOfStock(variant)
                ))
                .toList();
    }

    private ShopProductView.Variant resolvePrimaryVariant(ShopProductView product) {
        List<ShopProductView.Variant> variants = Optional.ofNullable(product.getVariants()).orElse(List.of());
        return variants.stream()
                .filter(ShopProductView.Variant::isActive)
                .filter(ShopProductView.Variant::isDefaultVariant)
                .findFirst()
                .orElseGet(() -> variants.stream().filter(ShopProductView.Variant::isActive).findFirst().orElse(null));
    }

    private String resolveInventoryStatus(ShopProductView product) {
        ShopProductView.Variant variant = resolvePrimaryVariant(product);
        if (variant != null && StringUtils.hasText(variant.getInventoryStatus())) {
            return variant.getInventoryStatus();
        }
        return defaultString(product.getInventoryStatus(), "OUT_OF_STOCK");
    }

    private boolean resolveOutOfStock(ShopProductView product) {
        ShopProductView.Variant variant = resolvePrimaryVariant(product);
        if (variant != null) {
            return isVariantOutOfStock(variant);
        }
        return isOutOfStock(defaultString(product.getInventoryStatus(), "OUT_OF_STOCK"), product.getQuantityAvailable(), product.getReservedQuantity());
    }

    private boolean isVariantOutOfStock(ShopProductView.Variant variant) {
        return !variant.isActive() || isOutOfStock(
                defaultString(variant.getInventoryStatus(), "OUT_OF_STOCK"),
                variant.getQuantityAvailable(),
                variant.getReservedQuantity()
        );
    }

    private boolean isOutOfStock(String inventoryStatus, Integer quantityAvailable, Integer reservedQuantity) {
        return "OUT_OF_STOCK".equalsIgnoreCase(inventoryStatus)
                || safeInteger(quantityAvailable) <= safeInteger(reservedQuantity);
    }

    private int promotionScore(ShopProductView product) {
        ShopProductView.Promotion promotion = product.getPromotion();
        if (promotion == null || !StringUtils.hasText(promotion.getStatus())) {
            return 0;
        }
        if (!"ACTIVE".equalsIgnoreCase(promotion.getStatus())) {
            return 0;
        }
        return promotion.getPriorityScore() == null ? 0 : promotion.getPriorityScore();
    }

    private String attributeValue(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("ATTRIBUTE_SERIALIZATION_FAILED", "Unable to serialize product attributes", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private int safeInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
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
                row.getClosesAt(),
                row.getRestaurantServiceType(),
                Boolean.TRUE.equals(row.getServesVeg()),
                Boolean.TRUE.equals(row.getServesNonVeg()),
                Boolean.TRUE.equals(row.getServesEgg())
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

    private record ProductWithShop(
            ShopProductView product,
            ShopShellView shop
    ) {
    }
}
