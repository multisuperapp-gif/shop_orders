package com.msa.shop_orders.internal.admin.catalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.internal.admin.catalog.dto.AdminCatalogDtos;
import com.msa.shop_orders.persistence.entity.InventoryEntity;
import com.msa.shop_orders.persistence.entity.ProductCouponRuleEntity;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.persistence.entity.ProductImageEntity;
import com.msa.shop_orders.persistence.entity.ProductPromotionEntity;
import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import com.msa.shop_orders.persistence.entity.ShopCategoryEntity;
import com.msa.shop_orders.persistence.entity.ShopDeliveryRuleEntity;
import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import com.msa.shop_orders.persistence.entity.UserEntity;
import com.msa.shop_orders.persistence.repository.InventoryRepository;
import com.msa.shop_orders.persistence.repository.ProductCouponRuleRepository;
import com.msa.shop_orders.persistence.repository.ProductImageRepository;
import com.msa.shop_orders.persistence.repository.ProductPromotionRepository;
import com.msa.shop_orders.persistence.repository.ProductRepository;
import com.msa.shop_orders.persistence.repository.ProductVariantRepository;
import com.msa.shop_orders.persistence.repository.ShopCategoryRepository;
import com.msa.shop_orders.persistence.repository.ShopDeliveryRuleRepository;
import com.msa.shop_orders.persistence.repository.ShopLocationRepository;
import com.msa.shop_orders.persistence.repository.UserRepository;
import com.msa.shop_orders.provider.shop.service.ShopProductWriteService;
import com.msa.shop_orders.provider.shop.service.ShopShellWriteService;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.ShopTypeView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopTypeViewRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InternalAdminCatalogService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UserRepository userRepository;
    private final ShopShellViewRepository shopShellViewRepository;
    private final ShopTypeViewRepository shopTypeViewRepository;
    private final ProductRepository productRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductPromotionRepository productPromotionRepository;
    private final ProductCouponRuleRepository productCouponRuleRepository;
    private final ShopLocationRepository shopLocationRepository;
    private final ShopDeliveryRuleRepository shopDeliveryRuleRepository;
    private final ObjectMapper objectMapper;
    private final ShopProductWriteService shopProductWriteService;
    private final ShopShellWriteService shopShellWriteService;

    public InternalAdminCatalogService(
            UserRepository userRepository,
            ShopShellViewRepository shopShellViewRepository,
            ShopTypeViewRepository shopTypeViewRepository,
            ProductRepository productRepository,
            ShopCategoryRepository shopCategoryRepository,
            ProductVariantRepository productVariantRepository,
            InventoryRepository inventoryRepository,
            ProductImageRepository productImageRepository,
            ProductPromotionRepository productPromotionRepository,
            ProductCouponRuleRepository productCouponRuleRepository,
            ShopLocationRepository shopLocationRepository,
            ShopDeliveryRuleRepository shopDeliveryRuleRepository,
            ObjectMapper objectMapper,
            ShopProductWriteService shopProductWriteService,
            ShopShellWriteService shopShellWriteService
    ) {
        this.userRepository = userRepository;
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopTypeViewRepository = shopTypeViewRepository;
        this.productRepository = productRepository;
        this.shopCategoryRepository = shopCategoryRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
        this.productImageRepository = productImageRepository;
        this.productPromotionRepository = productPromotionRepository;
        this.productCouponRuleRepository = productCouponRuleRepository;
        this.shopLocationRepository = shopLocationRepository;
        this.shopDeliveryRuleRepository = shopDeliveryRuleRepository;
        this.objectMapper = objectMapper;
        this.shopProductWriteService = shopProductWriteService;
        this.shopShellWriteService = shopShellWriteService;
    }

    public List<AdminCatalogDtos.ShopSummaryData> shops() {
        List<ShopShellView> shops = shopShellViewRepository.findAll();
        Map<Long, ShopTypeView> typesById = shopTypeViewRepository.findAllById(
                shops.stream().map(ShopShellView::getShopTypeId).filter(java.util.Objects::nonNull).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(ShopTypeView::getId, Function.identity()));
        Map<Long, UserEntity> usersById = userRepository.findAllById(
                shops.stream().map(ShopShellView::getOwnerUserId).filter(java.util.Objects::nonNull).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return shops.stream()
                .map(shop -> toShopSummary(shop, typesById.get(shop.getShopTypeId()), usersById.get(shop.getOwnerUserId())))
                .toList();
    }

    public AdminCatalogDtos.ShopDetailData shopDetail(Long shopId) {
        ShopShellView shop = requireShell(shopId);
        ShopTypeView type = shop.getShopTypeId() == null ? null : shopTypeViewRepository.findById(shop.getShopTypeId()).orElse(null);
        UserEntity owner = shop.getOwnerUserId() == null ? null : userRepository.findById(shop.getOwnerUserId()).orElse(null);
        List<AdminCatalogDtos.ProductSummaryData> products = products(shopId, null, null).stream()
                .sorted(Comparator.comparing(AdminCatalogDtos.ProductSummaryData::productId, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
        return new AdminCatalogDtos.ShopDetailData(
                shop.getShopId(),
                shop.getShopCode(),
                shop.getShopName(),
                type == null ? null : type.getName(),
                shop.getApprovalStatus(),
                shop.getOperationalStatus(),
                owner == null ? null : owner.getPublicUserId(),
                owner == null ? null : owner.getPhone(),
                owner == null ? null : owner.getEmail(),
                products
        );
    }

    public List<AdminCatalogDtos.ProductSummaryData> products(Long shopId, Long categoryId, Boolean active) {
        List<ProductEntity> products = productRepository.findAll().stream()
                .filter(product -> shopId == null || shopId.equals(product.getShopId()))
                .filter(product -> categoryId == null || categoryId.equals(product.getShopCategoryId()))
                .filter(product -> active == null || active.equals(product.isActive()))
                .toList();
        Map<Long, ShopShellView> shopsById = shopShellViewRepository.findAllById(
                products.stream().map(ProductEntity::getShopId).filter(java.util.Objects::nonNull).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(ShopShellView::getShopId, Function.identity()));
        Map<Long, ShopCategoryEntity> categoriesById = shopCategoryRepository.findAllById(
                products.stream().map(ProductEntity::getShopCategoryId).filter(java.util.Objects::nonNull).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(ShopCategoryEntity::getId, Function.identity()));
        return products.stream()
                .map(product -> {
                    ShopShellView shop = shopsById.get(product.getShopId());
                    ShopCategoryEntity category = categoriesById.get(product.getShopCategoryId());
                    return new AdminCatalogDtos.ProductSummaryData(
                            product.getId(),
                            product.getShopId(),
                            product.getShopCategoryId(),
                            product.getSku(),
                            product.getName(),
                            shop == null ? null : shop.getShopName(),
                            category == null ? null : category.getName(),
                            product.getProductType(),
                            product.isActive(),
                            product.isRequiresPrescription()
                    );
                })
                .toList();
    }

    public List<AdminCatalogDtos.ProductCategorySummaryData> productCategories(Long shopId) {
        ShopShellView shop = requireShell(shopId);
        ShopTypeView type = shop.getShopTypeId() == null ? null : shopTypeViewRepository.findById(shop.getShopTypeId()).orElse(null);
        List<ProductEntity> products = productRepository.findAll().stream()
                .filter(product -> shopId.equals(product.getShopId()))
                .toList();
        Map<Long, ShopCategoryEntity> categoriesById = shopCategoryRepository.findAllById(
                products.stream().map(ProductEntity::getShopCategoryId).filter(java.util.Objects::nonNull).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(ShopCategoryEntity::getId, Function.identity()));
        return products.stream()
                .collect(Collectors.groupingBy(ProductEntity::getShopCategoryId))
                .entrySet().stream()
                .map(entry -> {
                    ShopCategoryEntity category = categoriesById.get(entry.getKey());
                    return new AdminCatalogDtos.ProductCategorySummaryData(
                            entry.getKey(),
                            category == null ? null : category.getName(),
                            type == null ? null : type.getName(),
                            category != null && category.isActive(),
                            entry.getValue().size()
                    );
                })
                .toList();
    }

    public long countShopsWithLowStockAlerts(List<Long> shopIds) {
        Set<Long> scopedShopIds = shopIds == null ? Set.of() : shopIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (scopedShopIds.isEmpty()) {
            return 0L;
        }
        Set<Long> lowStockVariantIds = inventoryRepository.findAll().stream()
                .filter(inventory -> "LOW_STOCK".equalsIgnoreCase(inventory.getInventoryStatus())
                        || "OUT_OF_STOCK".equalsIgnoreCase(inventory.getInventoryStatus()))
                .map(InventoryEntity::getVariantId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (lowStockVariantIds.isEmpty()) {
            return 0L;
        }
        return lowStockVariantIds.stream()
                .map(productVariantRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(ProductVariantEntity::getProductId)
                .filter(java.util.Objects::nonNull)
                .map(productRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(ProductEntity::getShopId)
                .filter(scopedShopIds::contains)
                .distinct()
                .count();
    }

    public AdminCatalogDtos.ProductDetailData productDetail(Long productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found.", HttpStatus.NOT_FOUND));
        ShopShellView shop = requireShell(product.getShopId());
        ShopCategoryEntity category = shopCategoryRepository.findById(product.getShopCategoryId())
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Product category not found.", HttpStatus.NOT_FOUND));

        List<ProductVariantEntity> variants = productVariantRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        Map<Long, InventoryEntity> inventoryByVariantId = inventoryRepository.findByVariantIdIn(
                        variants.stream().map(ProductVariantEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(InventoryEntity::getVariantId, Function.identity(), (left, right) -> left));

        List<AdminCatalogDtos.ProductVariantData> variantData = variants.stream()
                .map(variant -> {
                    InventoryEntity inventory = inventoryByVariantId.get(variant.getId());
                    Map<String, Object> attributes = readJsonMap(variant.getAttributesJson());
                    return new AdminCatalogDtos.ProductVariantData(
                            variant.getId(),
                            variant.getVariantName(),
                            asString(attributes.get("colorName")),
                            asString(attributes.get("colorHex")),
                            variant.getUnitValue(),
                            variant.getUnitType(),
                            variant.getWeightInGrams(),
                            variant.getMrp(),
                            variant.getSellingPrice(),
                            variant.isDefaultVariant(),
                            variant.isActive(),
                            variant.getSortOrder(),
                            inventory == null ? null : inventory.getQuantityAvailable(),
                            inventory == null ? null : inventory.getReservedQuantity(),
                            inventory == null ? null : inventory.getInventoryStatus(),
                            attributes
                    );
                })
                .toList();

        List<AdminCatalogDtos.ProductImageData> images = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .map(image -> new AdminCatalogDtos.ProductImageData(
                        image.getId(),
                        image.getFileId(),
                        image.getImageRole(),
                        image.getVariantId(),
                        image.getSortOrder(),
                        image.isPrimaryImage()
                ))
                .toList();

        ProductPromotionEntity promotion = productPromotionRepository.findFirstByProductIdOrderByIdDesc(productId).orElse(null);
        ProductCouponRuleEntity coupon = productCouponRuleRepository.findFirstByProductIdOrderByIdDesc(productId).orElse(null);
        ShopDeliveryRuleEntity deliveryRule = shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shop.getShopId())
                .flatMap(location -> shopDeliveryRuleRepository.findByShopLocationId(location.getId()))
                .orElse(null);
        Long shopLocationId = shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shop.getShopId())
                .map(ShopLocationEntity::getId)
                .orElse(null);

        return new AdminCatalogDtos.ProductDetailData(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getShortDescription(),
                product.getDescription(),
                product.getProductType(),
                product.getBrandName(),
                product.isActive(),
                product.isFeatured(),
                product.isRequiresPrescription(),
                product.getAvgRating(),
                product.getTotalReviews(),
                product.getTotalOrders(),
                readJsonMap(product.getAttributesJson()),
                shop.getShopId(),
                shop.getShopName(),
                category.getId(),
                category.getName(),
                promotion == null ? null : new AdminCatalogDtos.ProductPromotionData(
                        promotion.getId(),
                        promotion.getPromotionType(),
                        promotion.getStartsAt(),
                        promotion.getEndsAt(),
                        promotion.getPriorityScore(),
                        promotion.getPaidAmount(),
                        promotion.getStatus()
                ),
                coupon == null ? null : new AdminCatalogDtos.ProductCouponData(
                        coupon.getId(),
                        coupon.getCouponCode(),
                        coupon.getCouponTitle(),
                        coupon.getDiscountType(),
                        coupon.getDiscountValue(),
                        coupon.getMinOrderAmount(),
                        coupon.getMaxDiscountAmount(),
                        coupon.getStartsAt(),
                        coupon.getEndsAt(),
                        coupon.isActive()
                ),
                deliveryRule == null ? null : new AdminCatalogDtos.ShopDeliveryRuleData(
                        shopLocationId,
                        deliveryRule.getDeliveryType(),
                        deliveryRule.getRadiusKm(),
                        deliveryRule.getMinOrderAmount(),
                        deliveryRule.getDeliveryFee(),
                        deliveryRule.getFreeDeliveryAbove(),
                        deliveryRule.getOrderCutoffMinutesBeforeClose(),
                        deliveryRule.getClosingSoonMinutes()
                ),
                images,
                variantData
        );
    }

    public AdminCatalogDtos.ShopSummaryData updateShopOperationalStatus(
            Long shopId,
            AdminCatalogDtos.ShopOperationalStatusUpdateRequest request
    ) {
        ShopShellView shell = shopShellWriteService.updateOperationalStatus(
                shopId,
                request == null ? null : request.operationalStatus()
        );
        ShopTypeView type = shell.getShopTypeId() == null ? null : shopTypeViewRepository.findById(shell.getShopTypeId()).orElse(null);
        UserEntity owner = shell.getOwnerUserId() == null ? null : userRepository.findById(shell.getOwnerUserId()).orElse(null);
        return toShopSummary(shell, type, owner);
    }

    public AdminCatalogDtos.ProductSummaryData updateProductStatus(
            Long productId,
            AdminCatalogDtos.ProductActiveUpdateRequest request
    ) {
        ProductEntity saved = shopProductWriteService.updateProductStatus(productId, request != null && request.active());
        return toProductSummary(saved);
    }

    public AdminCatalogDtos.ProductSummaryData updateProductFeatured(
            Long productId,
            AdminCatalogDtos.ProductFeaturedUpdateRequest request
    ) {
        ProductEntity saved = shopProductWriteService.updateProductFeatured(productId, request != null && request.featured());
        return toProductSummary(saved);
    }

    public AdminCatalogDtos.ProductDetailData updatePromotionStatus(
            Long productId,
            AdminCatalogDtos.ProductPromotionStatusUpdateRequest request
    ) {
        shopProductWriteService.updatePromotionStatus(productId, request == null ? null : request.status());
        return productDetail(productId);
    }

    public AdminCatalogDtos.ProductDetailData updateCouponStatus(
            Long productId,
            AdminCatalogDtos.ProductCouponActiveUpdateRequest request
    ) {
        shopProductWriteService.updateCouponStatus(productId, request != null && request.active());
        return productDetail(productId);
    }

    private ShopShellView requireShell(Long shopId) {
        return shopShellViewRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Shop not found.", HttpStatus.NOT_FOUND));
    }

    private ProductEntity requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found.", HttpStatus.NOT_FOUND));
    }

    private AdminCatalogDtos.ShopSummaryData toShopSummary(ShopShellView shop, ShopTypeView type, UserEntity owner) {
        return new AdminCatalogDtos.ShopSummaryData(
                shop.getShopId(),
                shop.getShopCode(),
                shop.getShopName(),
                type == null ? null : type.getName(),
                shop.getApprovalStatus(),
                shop.getOperationalStatus(),
                owner == null ? null : owner.getPublicUserId(),
                owner == null ? null : owner.getPhone(),
                owner == null ? null : owner.getEmail()
        );
    }

    private AdminCatalogDtos.ProductSummaryData toProductSummary(ProductEntity product) {
        ShopShellView shop = shopShellViewRepository.findById(product.getShopId())
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Shop not found.", HttpStatus.NOT_FOUND));
        ShopCategoryEntity category = product.getShopCategoryId() == null
                ? null
                : shopCategoryRepository.findById(product.getShopCategoryId()).orElse(null);
        return new AdminCatalogDtos.ProductSummaryData(
                product.getId(),
                product.getShopId(),
                product.getShopCategoryId(),
                product.getSku(),
                product.getName(),
                shop.getShopName(),
                category == null ? null : category.getName(),
                product.getProductType(),
                product.isActive(),
                product.isRequiresPrescription()
        );
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
