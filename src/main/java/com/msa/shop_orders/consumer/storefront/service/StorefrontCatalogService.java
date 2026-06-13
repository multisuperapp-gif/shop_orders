package com.msa.shop_orders.consumer.storefront.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.settings.ShopFeeSettingsService;
import com.msa.shop_orders.common.shoptype.RestaurantItemVisibilityPolicy;
import com.msa.shop_orders.common.shoptype.RestaurantVariantPromotionSupport;
import com.msa.shop_orders.consumer.storefront.dto.StorefrontDtos;
import com.msa.shop_orders.persistence.repository.StorefrontCatalogRepository;
import com.msa.shop_orders.provider.shop.dto.ShopProductDeliveryRuleData;
import com.msa.shop_orders.provider.shop.service.ShopCategoryViewService;
import com.msa.shop_orders.provider.shop.service.ShopDeliveryRuleViewService;
import com.msa.shop_orders.provider.shop.service.ShopOperatingHoursViewService;
import com.msa.shop_orders.provider.shop.service.ShopShellViewService;
import com.msa.shop_orders.provider.shop.view.ShopOperatingHoursView;
import com.msa.shop_orders.provider.shop.view.ShopProductView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter CLOSES_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final StorefrontCatalogRepository storefrontCatalogRepository;
    private final ShopCategoryViewService shopCategoryViewService;
    private final ShopProductViewRepository shopProductViewRepository;
    private final ShopShellViewRepository shopShellViewRepository;
    private final ShopShellViewService shopShellViewService;
    private final ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    private final ShopOperatingHoursViewService shopOperatingHoursViewService;
    private final ShopFeeSettingsService shopFeeSettingsService;
    private final ObjectMapper objectMapper;

    public StorefrontCatalogService(
            StorefrontCatalogRepository storefrontCatalogRepository,
            ShopCategoryViewService shopCategoryViewService,
            ShopProductViewRepository shopProductViewRepository,
            ShopShellViewRepository shopShellViewRepository,
            ShopShellViewService shopShellViewService,
            ShopDeliveryRuleViewService shopDeliveryRuleViewService,
            ShopOperatingHoursViewService shopOperatingHoursViewService,
            ShopFeeSettingsService shopFeeSettingsService,
            ObjectMapper objectMapper
    ) {
        this.storefrontCatalogRepository = storefrontCatalogRepository;
        this.shopCategoryViewService = shopCategoryViewService;
        this.shopProductViewRepository = shopProductViewRepository;
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopShellViewService = shopShellViewService;
        this.shopDeliveryRuleViewService = shopDeliveryRuleViewService;
        this.shopOperatingHoursViewService = shopOperatingHoursViewService;
        this.shopFeeSettingsService = shopFeeSettingsService;
        this.objectMapper = objectMapper;
    }

    public StorefrontDtos.HomeBootstrapData homeBootstrap(Double latitude, Double longitude, int page, int size) {
        return new StorefrontDtos.HomeBootstrapData(
                findShopTypes(),
                findProducts(null, null, null, latitude, longitude, page, size),
                shopFeeSettingsService.shopPlatformFeePercent()
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
                shops(normalizedShopType, null, null, latitude, longitude, page, size)
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
            Long categoryId,
            String search,
            Double latitude,
            Double longitude,
            int page,
            int size
    ) {
        StorefrontDtos.ShopTypeData shopType = requireShopType(normalizedShopType);
        return findShops(shopType.id(), categoryId, search, latitude, longitude, page, size);
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
        String normalizedSearch = normalizeSearch(search);

        List<StorefrontDtos.ShopSummaryData> all = storefrontCatalogRepository
                .findApprovedShopsByType(shopTypeId).stream()
                // Shop-wise listing only shows shops that have at least one category-eligible active product.
                .filter(shop -> categoryId == null || shopHasActiveProductInCategory(shop.getShopId(), categoryId))
                .filter(shop -> matchesShopSearch(shop, normalizedSearch))
                .map(this::buildShopSummary)
                .sorted(shopSummaryComparator(userLatitude, userLongitude))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, all.size());
        int toIndex = Math.min(fromIndex + safeSize, all.size());
        boolean hasMore = toIndex < all.size();
        return new StorefrontDtos.PageResponse<>(all.subList(fromIndex, toIndex), safePage, safeSize, hasMore);
    }

    private StorefrontDtos.ShopSummaryData requireShop(Long shopTypeId, Long shopId) {
        return storefrontCatalogRepository.findApprovedShopById(shopTypeId, shopId)
                .map(this::buildShopSummary)
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Shop not found", HttpStatus.NOT_FOUND));
    }

    private boolean shopHasActiveProductInCategory(Long shopId, Long categoryId) {
        return shopProductViewRepository.findByShopIdOrderByUpdatedAtDesc(shopId).stream()
                .anyMatch(product -> isActiveProduct(product)
                        && Objects.equals(product.getCategoryId(), categoryId));
    }

    private boolean matchesShopSearch(
            StorefrontCatalogRepository.ShopIdentityLocationView shop,
            String normalizedSearch
    ) {
        if (normalizedSearch == null) {
            return true;
        }
        return containsIgnoreCase(shop.getShopName(), normalizedSearch)
                || containsIgnoreCase(shop.getCity(), normalizedSearch);
    }

    private Comparator<StorefrontDtos.ShopSummaryData> shopSummaryComparator(
            Double userLatitude,
            Double userLongitude
    ) {
        return Comparator
                .comparing((StorefrontDtos.ShopSummaryData shop) -> shop.acceptsOrders() ? 0 : 1)
                .thenComparing(shop -> shop.closingSoon() ? 1 : 0)
                .thenComparingDouble(shop -> distanceKm(userLatitude, userLongitude, shop.latitude(), shop.longitude()))
                .thenComparing(shop -> safeBigDecimal(shop.avgRating()), Comparator.reverseOrder())
                .thenComparing(StorefrontDtos.ShopSummaryData::totalReviews, Comparator.reverseOrder());
    }

    private double distanceKm(Double userLat, Double userLng, BigDecimal shopLat, BigDecimal shopLng) {
        if (userLat == null || userLng == null || shopLat == null || shopLng == null) {
            return Double.MAX_VALUE;
        }
        double lat1 = Math.toRadians(userLat);
        double lat2 = Math.toRadians(shopLat.doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(shopLng.doubleValue() - userLng);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Builds a shop summary by combining SQL identity/location with MongoDB views:
     * delivery rule, today's operating hours (open/closing/accepting), and the
     * veg/non-veg/egg flags derived from the shop's active products.
     */
    private StorefrontDtos.ShopSummaryData buildShopSummary(
            StorefrontCatalogRepository.ShopIdentityLocationView shop
    ) {
        Long shopId = shop.getShopId();
        ShopProductDeliveryRuleData delivery = shopDeliveryRuleViewService
                .findPrimaryDeliveryRule(shopId).orElse(null);
        OperatingState operating = resolveOperatingState(shopId, delivery);
        VegProfile veg = resolveVegProfile(shopId, shop.getRestaurantServiceType());
        boolean hasOffer = resolveHasOffer(shopId);

        return new StorefrontDtos.ShopSummaryData(
                shopId,
                shop.getShopTypeId(),
                shop.getShopName(),
                shop.getShopCode(),
                shop.getLogoObjectKey(),
                shop.getCoverObjectKey(),
                shop.getAvgRating(),
                shop.getTotalReviews() == null ? 0L : shop.getTotalReviews(),
                shop.getCity(),
                shop.getLatitude(),
                shop.getLongitude(),
                delivery == null ? null : delivery.deliveryType(),
                delivery == null ? null : delivery.radiusKm(),
                delivery == null ? null : delivery.minOrderAmount(),
                delivery == null ? null : delivery.deliveryFee(),
                operating.openNow(),
                operating.closingSoon(),
                operating.acceptsOrders(),
                operating.closesAt(),
                veg.restaurantServiceType(),
                veg.servesVeg(),
                veg.servesNonVeg(),
                veg.servesEgg(),
                hasOffer,
                shop.getOperationalStatus(),
                resolveCouponLabel(shopId)
        );
    }

    // A shop "has an offer" when it has a restaurant coupon that is active and
    // currently within its start/end window.
    private boolean resolveHasOffer(Long shopId) {
        return shopShellViewRepository.findById(shopId)
                .map(ShopShellView::getRestaurantCoupon)
                .map(this::isCouponActive)
                .orElse(false);
    }

    // A short ribbon label for the shop card when an active coupon exists, e.g.
    // "50% OFF" (percentage) or "FLAT ₹100 OFF" (flat). Null when no live coupon.
    private String resolveCouponLabel(Long shopId) {
        return shopShellViewRepository.findById(shopId)
                .map(ShopShellView::getRestaurantCoupon)
                .filter(this::isCouponActive)
                .map(this::buildCouponLabel)
                .orElse(null);
    }

    private String buildCouponLabel(ShopShellView.RestaurantCoupon coupon) {
        if (coupon == null || coupon.getDiscountValue() == null) {
            return null;
        }
        String type = coupon.getDiscountType() == null
                ? "" : coupon.getDiscountType().trim().toUpperCase();
        int value = coupon.getDiscountValue().intValue();
        if (value <= 0) {
            return null;
        }
        if ("PERCENTAGE".equals(type)) {
            return value + "% OFF";
        }
        if ("FLAT".equals(type)) {
            return "FLAT ₹" + value + " OFF";
        }
        return null;
    }

    private boolean isCouponActive(ShopShellView.RestaurantCoupon coupon) {
        if (coupon == null || !Boolean.TRUE.equals(coupon.getActive())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(SHOP_ZONE);
        if (coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())) {
            return false;
        }
        if (coupon.getEndsAt() != null && now.isAfter(coupon.getEndsAt())) {
            return false;
        }
        return true;
    }

    private OperatingState resolveOperatingState(Long shopId, ShopProductDeliveryRuleData delivery) {
        LocalDate today = LocalDate.now(SHOP_ZONE);
        LocalTime now = LocalTime.now(SHOP_ZONE);
        // Operating hours are stored with ISO weekday: Monday=1 .. Sunday=7
        // (see ShopOperatingHoursServiceImpl.saveCurrent).
        int weekday = today.getDayOfWeek().getValue();
        ShopOperatingHoursView hours = shopOperatingHoursViewService
                .findByShopIdAndWeekday(shopId, weekday).orElse(null);
        if (hours == null || hours.isClosed()) {
            return new OperatingState(false, false, false, null);
        }
        LocalTime open = parseTime(hours.getOpenTime());
        LocalTime close = parseTime(hours.getCloseTime());
        if (open == null || close == null) {
            return new OperatingState(false, false, false, null);
        }
        boolean openNow = !now.isBefore(open) && !now.isAfter(close);
        String closesAt = close.format(CLOSES_AT_FORMAT);
        if (!openNow) {
            return new OperatingState(false, false, false, closesAt);
        }
        int closingSoonMinutes = delivery == null || delivery.closingSoonMinutes() == null
                ? 60 : delivery.closingSoonMinutes();
        int cutoffMinutes = delivery == null || delivery.orderCutoffMinutesBeforeClose() == null
                ? 30 : delivery.orderCutoffMinutesBeforeClose();
        boolean closingSoon = !now.isBefore(close.minusMinutes(closingSoonMinutes));
        boolean acceptsOrders = !now.isAfter(close.minusMinutes(cutoffMinutes));
        return new OperatingState(true, closingSoon, acceptsOrders, closesAt);
    }

    private VegProfile resolveVegProfile(Long shopId, String configuredServiceType) {
        boolean servesVeg = false;
        boolean servesNonVeg = false;
        boolean servesEgg = false;
        for (ShopProductView product : shopProductViewRepository.findByShopIdOrderByUpdatedAtDesc(shopId)) {
            if (!isActiveProduct(product)) {
                continue;
            }
            String pref = attributeValue(product.getAttributes(), "foodPreference");
            if (pref == null) {
                continue;
            }
            switch (pref.trim().toUpperCase()) {
                case "VEG" -> servesVeg = true;
                case "NON_VEG" -> servesNonVeg = true;
                case "EGG" -> servesEgg = true;
                default -> { }
            }
        }
        String configured = configuredServiceType == null ? "" : configuredServiceType.trim();
        String resolvedType;
        if (!configured.isEmpty()) {
            resolvedType = configured;
        } else if (servesVeg && !servesNonVeg && !servesEgg) {
            resolvedType = "PURE_VEG";
        } else if (servesVeg) {
            resolvedType = "VEG_NON_VEG";
        } else if (servesNonVeg || servesEgg) {
            resolvedType = "PURE_NON_VEG";
        } else {
            resolvedType = "NOT_SET";
        }
        // Honor an explicit shop-level service type for the veg/non-veg badges.
        String upperConfigured = configured.toUpperCase();
        if (upperConfigured.equals("PURE_VEG")) {
            servesVeg = true;
            servesNonVeg = false;
            servesEgg = false;
        } else if (upperConfigured.equals("PURE_NON_VEG")) {
            servesVeg = false;
        }
        return new VegProfile(resolvedType, servesVeg, servesNonVeg, servesEgg);
    }

    private LocalTime parseTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            return LocalTime.parse(value.length() > 5 ? value.substring(0, 5) : value);
        } catch (Exception ex) {
            return null;
        }
    }

    private record OperatingState(boolean openNow, boolean closingSoon, boolean acceptsOrders, String closesAt) {
    }

    private record VegProfile(String restaurantServiceType, boolean servesVeg, boolean servesNonVeg, boolean servesEgg) {
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
            // toShellViewAndSync re-reads from SQL and writes back to MongoDB so any
            // shop whose MongoDB shell is stale self-heals on the next storefront load.
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
        boolean shopAcceptsOrders = false;
        if (shop != null && shop.getShopId() != null) {
            ShopProductDeliveryRuleData shopDelivery = shopDeliveryRuleViewService
                    .findPrimaryDeliveryRule(shop.getShopId()).orElse(null);
            shopAcceptsOrders = resolveOperatingState(shop.getShopId(), shopDelivery).acceptsOrders();
        }
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
                variant == null
                        ? product.getMrp()
                        : RestaurantVariantPromotionSupport.resolveDisplayOriginalPrice(product, variant),
                variant == null
                        ? product.getSellingPrice()
                        : RestaurantVariantPromotionSupport.resolveEffectiveSellingPrice(product, variant),
                safeBigDecimal(product.getAvgRating()),
                safeLong(product.getTotalReviews()),
                safeLong(product.getTotalOrders()),
                resolveInventoryStatus(product),
                resolveOutOfStock(product),
                promotionScore(product),
                null,
                toJson(product.getAttributes()),
                attributeValue(product.getAttributes(), "foodPreference"),
                product.isFeatured(),
                shopAcceptsOrders
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
                        RestaurantVariantPromotionSupport.resolveDisplayOriginalPrice(product, variant),
                        RestaurantVariantPromotionSupport.resolveEffectiveSellingPrice(product, variant),
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
        List<ShopProductView.Variant> variants = product.getVariants();
        if (variants != null && !variants.isEmpty()) {
            // A multi-size item is "out of stock" on the menu card only when
            // EVERY variant is out — if any size is available the item stays
            // orderable (per-variant out-of-stock is shown on the detail page).
            return variants.stream().allMatch(this::isVariantOutOfStock);
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
        if (RestaurantVariantPromotionSupport.hasActivePromotion(product)
                && (product.getVariants() == null || product.getVariants().size() > 1)) {
            return 1;
        }
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
