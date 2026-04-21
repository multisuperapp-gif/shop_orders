package com.msa.shop_orders.provider.shop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.*;
import com.msa.shop_orders.persistence.repository.*;
import com.msa.shop_orders.provider.shop.dto.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app.mongodb", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ShopProductServiceImpl implements ShopProductService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern COLOR_HEX_PATTERN = Pattern.compile("^#?[0-9A-Fa-f]{6}$");

    private final ShopContextService shopContextService;
    private final ShopInventoryCategoryRepository shopInventoryCategoryRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductPromotionRepository productPromotionRepository;
    private final ProductCouponRuleRepository productCouponRuleRepository;
    private final ShopLocationRepository shopLocationRepository;
    private final ShopDeliveryRuleRepository shopDeliveryRuleRepository;
    private final ShopProductActivityLogService shopProductActivityLogService;
    private final ObjectMapper objectMapper;

    public ShopProductServiceImpl(
            ShopContextService shopContextService,
            ShopInventoryCategoryRepository shopInventoryCategoryRepository,
            ShopCategoryRepository shopCategoryRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            InventoryRepository inventoryRepository,
            ProductImageRepository productImageRepository,
            ProductPromotionRepository productPromotionRepository,
            ProductCouponRuleRepository productCouponRuleRepository,
            ShopLocationRepository shopLocationRepository,
            ShopDeliveryRuleRepository shopDeliveryRuleRepository,
            ShopProductActivityLogService shopProductActivityLogService,
            ObjectMapper objectMapper
    ) {
        this.shopContextService = shopContextService;
        this.shopInventoryCategoryRepository = shopInventoryCategoryRepository;
        this.shopCategoryRepository = shopCategoryRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
        this.productImageRepository = productImageRepository;
        this.productPromotionRepository = productPromotionRepository;
        this.productCouponRuleRepository = productCouponRuleRepository;
        this.shopLocationRepository = shopLocationRepository;
        this.shopDeliveryRuleRepository = shopDeliveryRuleRepository;
        this.shopProductActivityLogService = shopProductActivityLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ShopProductData> products(Long categoryId) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        Map<Long, ShopCategoryEntity> mappedShopCategories = mappedShopCategories(shopEntity);
        Map<String, Long> categoryIdByName = mappedShopCategories.values().stream()
                .collect(Collectors.toMap(category -> normalize(category.getName()), ShopCategoryEntity::getId, (left, right) -> left, LinkedHashMap::new));

        String selectedCategoryName = null;
        if (categoryId != null) {
            ShopCategoryEntity selectedCategory = mappedShopCategories.get(categoryId);
            if (selectedCategory == null) {
                throw new BusinessException("CATEGORY_NOT_ADDED", "Selected category is not added for this shop.", HttpStatus.BAD_REQUEST);
            }
            selectedCategoryName = normalize(selectedCategory.getName());
        }

        List<ProductEntity> products = productRepository.findByShopIdOrderByUpdatedAtDesc(shopEntity.getId());
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, ShopCategoryEntity> productCategories = shopCategoryRepository.findAllById(products.stream().map(ProductEntity::getShopCategoryId).collect(Collectors.toSet()))
                .stream()
                .filter(ShopCategoryEntity::isActive)
                .collect(Collectors.toMap(ShopCategoryEntity::getId, Function.identity()));
        if (selectedCategoryName != null) {
            String finalSelectedCategoryName = selectedCategoryName;
            products = products.stream()
                    .filter(product -> {
                        ShopCategoryEntity category = productCategories.get(product.getShopCategoryId());
                        return category != null && finalSelectedCategoryName.equals(normalize(category.getName()));
                    })
                    .toList();
        }
        return buildProductData(products, productCategories, categoryIdByName, resolveDeliveryRule(shopEntity.getId()));
    }

    @Override
    @Transactional
    public ShopProductData createProduct(ShopCreateProductRequest request) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ShopCategoryEntity shopCategoryEntity = requireMappedCategory(shopEntity, request.categoryId());

        ProductEntity productEntity = new ProductEntity();
        productEntity.setShopId(shopEntity.getId());
        productEntity.setShopCategoryId(shopCategoryEntity.getId());
        productEntity.setSku(resolveSku(request.sku(), shopEntity.getId(), shopCategoryEntity.getId(), request.itemName()));
        productEntity.setName(normalizeDisplayText(request.itemName()));
        productEntity.setShortDescription(blankToNull(request.shortDescription()));
        productEntity.setDescription(blankToNull(request.description()));
        productEntity.setProductType(resolveProductType(request.productType()));
        productEntity.setBrandName(blankToNull(request.brandName()));
        productEntity.setAttributesJson(writeJson(request.attributes()));
        productEntity.setRequiresPrescription(Boolean.TRUE.equals(request.requiresPrescription()));
        productEntity.setAvgRating(BigDecimal.ZERO.setScale(2));
        productEntity.setTotalReviews(0);
        productEntity.setTotalOrders(0);
        productEntity.setActive(request.active() == null || request.active());
        productEntity.setFeatured(Boolean.TRUE.equals(request.featured()));
        productEntity = productRepository.save(productEntity);

        VariantSaveResult variantSaveResult = saveVariants(productEntity, request, false);
        saveImages(productEntity.getId(), request, variantSaveResult.variantIdsByClientKey(), false);
        savePromotion(productEntity, request.promotion(), false);
        saveCoupon(productEntity, request.coupon(), false);
        shopProductActivityLogService.productCreated(shopEntity, productEntity);

        Long savedProductId = productEntity.getId();
        return products(request.categoryId()).stream()
                .filter(product -> Objects.equals(product.productId(), savedProductId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRODUCT_SAVE_FAILED", "Unable to load saved product.", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Override
    @Transactional
    public ShopProductData updateProduct(Long productId, ShopCreateProductRequest request) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ProductEntity productEntity = productRepository.findByIdAndShopId(productId, shopEntity.getId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
        ShopCategoryEntity shopCategoryEntity = requireMappedCategory(shopEntity, request.categoryId());

        productEntity.setShopCategoryId(shopCategoryEntity.getId());
        productEntity.setName(normalizeDisplayText(request.itemName()));
        productEntity.setShortDescription(blankToNull(request.shortDescription()));
        productEntity.setDescription(blankToNull(request.description()));
        productEntity.setProductType(resolveProductType(request.productType()));
        productEntity.setBrandName(blankToNull(request.brandName()));
        productEntity.setAttributesJson(writeJson(request.attributes()));
        productEntity.setRequiresPrescription(Boolean.TRUE.equals(request.requiresPrescription()));
        productEntity.setActive(request.active() == null || request.active());
        productEntity.setFeatured(Boolean.TRUE.equals(request.featured()));
        if (request.sku() != null && !request.sku().isBlank()) {
            String normalizedSku = request.sku().trim();
            if (!normalizedSku.equalsIgnoreCase(productEntity.getSku()) && productRepository.existsBySkuIgnoreCase(normalizedSku)) {
                throw new BusinessException("DUPLICATE_SKU", "SKU already exists.", HttpStatus.BAD_REQUEST);
            }
            productEntity.setSku(normalizedSku);
        }
        productRepository.save(productEntity);

        VariantSaveResult variantSaveResult = saveVariants(productEntity, request, true);
        saveImages(productEntity.getId(), request, variantSaveResult.variantIdsByClientKey(), true);
        savePromotion(productEntity, request.promotion(), true);
        saveCoupon(productEntity, request.coupon(), true);
        shopProductActivityLogService.productUpdated(shopEntity, productEntity);

        return products(request.categoryId()).stream()
                .filter(product -> Objects.equals(product.productId(), productEntity.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRODUCT_SAVE_FAILED", "Unable to load updated product.", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Override
    @Transactional
    public ShopProductData duplicateProduct(Long productId) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ProductEntity sourceProduct = productRepository.findByIdAndShopId(productId, shopEntity.getId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
        requireMappedCategory(shopEntity, sourceProduct.getShopCategoryId());

        ProductEntity duplicateProduct = new ProductEntity();
        duplicateProduct.setShopId(sourceProduct.getShopId());
        duplicateProduct.setShopCategoryId(sourceProduct.getShopCategoryId());
        duplicateProduct.setSku(resolveDuplicateSku(sourceProduct.getSku()));
        duplicateProduct.setName(resolveDuplicateItemName(sourceProduct.getName()));
        duplicateProduct.setShortDescription(sourceProduct.getShortDescription());
        duplicateProduct.setDescription(sourceProduct.getDescription());
        duplicateProduct.setProductType(sourceProduct.getProductType());
        duplicateProduct.setBrandName(sourceProduct.getBrandName());
        duplicateProduct.setAttributesJson(sourceProduct.getAttributesJson());
        duplicateProduct.setRequiresPrescription(sourceProduct.isRequiresPrescription());
        duplicateProduct.setAvgRating(BigDecimal.ZERO.setScale(2));
        duplicateProduct.setTotalReviews(0);
        duplicateProduct.setTotalOrders(0);
        duplicateProduct.setActive(false);
        duplicateProduct.setFeatured(false);
        duplicateProduct = productRepository.save(duplicateProduct);

        Map<Long, Long> variantIdsBySourceId = copyVariants(sourceProduct, duplicateProduct);
        copyImages(sourceProduct.getId(), duplicateProduct.getId(), variantIdsBySourceId);
        copyPromotion(sourceProduct.getId(), duplicateProduct);
        copyCoupon(sourceProduct.getId(), duplicateProduct);
        shopProductActivityLogService.productDuplicated(shopEntity, sourceProduct, duplicateProduct);

        Long duplicatedProductId = duplicateProduct.getId();
        return products(null).stream()
                .filter(product -> Objects.equals(product.productId(), duplicatedProductId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRODUCT_SAVE_FAILED", "Unable to load duplicated product.", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Override
    @Transactional
    public ShopProductData updateProductStatus(Long productId, ShopProductStatusUpdateRequest request) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ProductEntity productEntity = productRepository.findByIdAndShopId(productId, shopEntity.getId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));

        boolean active = Boolean.TRUE.equals(request.active());
        productEntity.setActive(active);
        if (!active) {
            productEntity.setFeatured(false);
        }
        productRepository.save(productEntity);

        List<ProductVariantEntity> variants = productVariantRepository.findByProductIdOrderBySortOrderAscIdAsc(productEntity.getId());
        for (ProductVariantEntity variant : variants) {
            inventoryRepository.findByVariantId(variant.getId()).ifPresent(inventory -> {
                inventory.setInventoryStatus(resolveInventoryStatus(
                        inventory.getQuantityAvailable(),
                        inventory.getReorderLevel(),
                        productEntity.isActive() && variant.isActive()
                ));
                inventoryRepository.save(inventory);
            });
        }
        shopProductActivityLogService.productStatusChanged(shopEntity, productEntity, active);

        return products(null).stream()
                .filter(product -> Objects.equals(product.productId(), productEntity.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRODUCT_SAVE_FAILED", "Unable to load updated product status.", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Override
    @Transactional
    public void removeProduct(Long productId) {
        updateProductStatus(productId, new ShopProductStatusUpdateRequest(false));
    }

    @Override
    public List<ShopProductActivityData> productActivity(Long productId) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        productRepository.findByIdAndShopId(productId, shopEntity.getId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
        return shopProductActivityLogService.productActivity(shopEntity.getId(), productId);
    }

    private VariantSaveResult saveVariants(ProductEntity productEntity, ShopCreateProductRequest request, boolean updateMode) {
        List<ShopProductVariantRequest> requestedVariants = normalizeVariantRequests(request);
        if (requestedVariants.isEmpty()) {
            throw new BusinessException("PRODUCT_VARIANT_REQUIRED", "At least one product variant is required.", HttpStatus.BAD_REQUEST);
        }

        if (requestedVariants.size() == 1 && request.variants() == null) {
            return saveLegacyVariant(productEntity, requestedVariants.getFirst());
        }

        List<ProductVariantEntity> existingVariants = productVariantRepository.findByProductIdOrderBySortOrderAscIdAsc(productEntity.getId());
        Map<Long, ProductVariantEntity> existingById = existingVariants.stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        int defaultIndex = resolveDefaultVariantIndex(requestedVariants);
        Map<String, Long> variantIdsByClientKey = new LinkedHashMap<>();

        for (int index = 0; index < requestedVariants.size(); index++) {
            ShopProductVariantRequest requestedVariant = requestedVariants.get(index);
            validateVariantRequest(requestedVariant);

            ProductVariantEntity variantEntity = requestedVariant.variantId() == null
                    ? new ProductVariantEntity()
                    : existingById.remove(requestedVariant.variantId());
            if (variantEntity == null) {
                variantEntity = new ProductVariantEntity();
            }
            variantEntity.setProductId(productEntity.getId());
            variantEntity.setVariantName(resolveVariantName(productEntity.getName(), requestedVariant, index));
            variantEntity.setAttributesJson(writeJson(mergeVariantAttributes(requestedVariant)));
            variantEntity.setUnitValue(requestedVariant.unitValue());
            variantEntity.setUnitType(blankToNull(requestedVariant.unitType()));
            variantEntity.setWeightInGrams(requestedVariant.weightInGrams());
            variantEntity.setMrp(requestedVariant.mrp());
            variantEntity.setSellingPrice(requestedVariant.sellingPrice());
            variantEntity.setDefaultVariant(index == defaultIndex);
            variantEntity.setSortOrder(requestedVariant.sortOrder() == null ? index : requestedVariant.sortOrder());
            variantEntity.setActive(requestedVariant.active() == null || requestedVariant.active());
            ProductVariantEntity savedVariant = productVariantRepository.save(variantEntity);

            InventoryEntity inventoryEntity = inventoryRepository.findByVariantId(savedVariant.getId()).orElseGet(InventoryEntity::new);
            inventoryEntity.setVariantId(savedVariant.getId());
            inventoryEntity.setQuantityAvailable(zeroIfNull(requestedVariant.quantityAvailable()));
            inventoryEntity.setReservedQuantity(inventoryEntity.getReservedQuantity() == null ? 0 : inventoryEntity.getReservedQuantity());
            inventoryEntity.setReorderLevel(requestedVariant.reorderLevel());
            inventoryEntity.setInventoryStatus(resolveInventoryStatus(
                    requestedVariant.quantityAvailable(),
                    requestedVariant.reorderLevel(),
                    productEntity.isActive() && savedVariant.isActive()
            ));
            inventoryRepository.save(inventoryEntity);
            variantIdsByClientKey.put(resolveVariantClientKey(requestedVariant, index), savedVariant.getId());
        }

        if (updateMode) {
            for (ProductVariantEntity existingVariant : existingById.values()) {
                existingVariant.setDefaultVariant(false);
                existingVariant.setActive(false);
                productVariantRepository.save(existingVariant);
                inventoryRepository.findByVariantId(existingVariant.getId()).ifPresent(inventory -> {
                    inventory.setInventoryStatus(resolveInventoryStatus(
                            inventory.getQuantityAvailable(),
                            inventory.getReorderLevel(),
                            productEntity.isActive() && existingVariant.isActive()
                    ));
                    inventoryRepository.save(inventory);
                });
            }
        }

        return new VariantSaveResult(variantIdsByClientKey);
    }

    private VariantSaveResult saveLegacyVariant(ProductEntity productEntity, ShopProductVariantRequest requestedVariant) {
        ProductVariantEntity variantEntity = productVariantRepository.findByProductIdAndDefaultVariantTrue(productEntity.getId())
                .orElseGet(ProductVariantEntity::new);
        variantEntity.setProductId(productEntity.getId());
        variantEntity.setVariantName(resolveVariantName(productEntity.getName(), requestedVariant, 0));
        variantEntity.setAttributesJson(writeJson(mergeVariantAttributes(requestedVariant)));
        variantEntity.setUnitValue(requestedVariant.unitValue());
        variantEntity.setUnitType(blankToNull(requestedVariant.unitType()));
        variantEntity.setWeightInGrams(requestedVariant.weightInGrams());
        variantEntity.setMrp(requestedVariant.mrp());
        variantEntity.setSellingPrice(requestedVariant.sellingPrice());
        variantEntity.setDefaultVariant(true);
        variantEntity.setSortOrder(0);
        variantEntity.setActive(requestedVariant.active() == null || requestedVariant.active());
        ProductVariantEntity savedVariant = productVariantRepository.save(variantEntity);

        InventoryEntity inventoryEntity = inventoryRepository.findByVariantId(savedVariant.getId()).orElseGet(InventoryEntity::new);
        inventoryEntity.setVariantId(savedVariant.getId());
        inventoryEntity.setQuantityAvailable(zeroIfNull(requestedVariant.quantityAvailable()));
        inventoryEntity.setReservedQuantity(inventoryEntity.getReservedQuantity() == null ? 0 : inventoryEntity.getReservedQuantity());
        inventoryEntity.setReorderLevel(requestedVariant.reorderLevel());
        inventoryEntity.setInventoryStatus(resolveInventoryStatus(
                requestedVariant.quantityAvailable(),
                requestedVariant.reorderLevel(),
                productEntity.isActive() && savedVariant.isActive()
        ));
        inventoryRepository.save(inventoryEntity);
        return new VariantSaveResult(Map.of(resolveVariantClientKey(requestedVariant, 0), savedVariant.getId()));
    }

    private Map<Long, Long> copyVariants(ProductEntity sourceProduct, ProductEntity duplicateProduct) {
        List<ProductVariantEntity> sourceVariants = productVariantRepository.findByProductIdOrderBySortOrderAscIdAsc(sourceProduct.getId());
        LinkedHashMap<Long, Long> variantIdsBySourceId = new LinkedHashMap<>();
        for (ProductVariantEntity sourceVariant : sourceVariants) {
            ProductVariantEntity duplicateVariant = new ProductVariantEntity();
            duplicateVariant.setProductId(duplicateProduct.getId());
            duplicateVariant.setVariantName(sourceVariant.getVariantName());
            duplicateVariant.setAttributesJson(sourceVariant.getAttributesJson());
            duplicateVariant.setUnitValue(sourceVariant.getUnitValue());
            duplicateVariant.setUnitType(sourceVariant.getUnitType());
            duplicateVariant.setWeightInGrams(sourceVariant.getWeightInGrams());
            duplicateVariant.setMrp(sourceVariant.getMrp());
            duplicateVariant.setSellingPrice(sourceVariant.getSellingPrice());
            duplicateVariant.setDefaultVariant(sourceVariant.isDefaultVariant());
            duplicateVariant.setSortOrder(sourceVariant.getSortOrder());
            duplicateVariant.setActive(sourceVariant.isActive());
            ProductVariantEntity savedVariant = productVariantRepository.save(duplicateVariant);

            InventoryEntity sourceInventory = inventoryRepository.findByVariantId(sourceVariant.getId()).orElse(null);
            InventoryEntity duplicateInventory = new InventoryEntity();
            duplicateInventory.setVariantId(savedVariant.getId());
            duplicateInventory.setQuantityAvailable(sourceInventory == null ? 0 : zeroIfNull(sourceInventory.getQuantityAvailable()));
            duplicateInventory.setReservedQuantity(0);
            duplicateInventory.setReorderLevel(sourceInventory == null ? null : sourceInventory.getReorderLevel());
            duplicateInventory.setInventoryStatus(resolveInventoryStatus(
                    duplicateInventory.getQuantityAvailable(),
                    duplicateInventory.getReorderLevel(),
                    duplicateProduct.isActive() && savedVariant.isActive()
            ));
            inventoryRepository.save(duplicateInventory);
            variantIdsBySourceId.put(sourceVariant.getId(), savedVariant.getId());
        }
        return variantIdsBySourceId;
    }

    private void copyImages(Long sourceProductId, Long duplicateProductId, Map<Long, Long> variantIdsBySourceId) {
        List<ProductImageEntity> sourceImages = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(sourceProductId);
        for (ProductImageEntity sourceImage : sourceImages) {
            ProductImageEntity duplicateImage = new ProductImageEntity();
            duplicateImage.setProductId(duplicateProductId);
            duplicateImage.setVariantId(sourceImage.getVariantId() == null ? null : variantIdsBySourceId.get(sourceImage.getVariantId()));
            duplicateImage.setFileId(sourceImage.getFileId());
            duplicateImage.setImageRole(sourceImage.getImageRole());
            duplicateImage.setSortOrder(sourceImage.getSortOrder());
            duplicateImage.setPrimaryImage(sourceImage.isPrimaryImage());
            productImageRepository.save(duplicateImage);
        }
    }

    private void copyPromotion(Long sourceProductId, ProductEntity duplicateProduct) {
        ProductPromotionEntity sourcePromotion = productPromotionRepository.findFirstByProductIdOrderByIdDesc(sourceProductId).orElse(null);
        if (sourcePromotion == null) {
            return;
        }
        ProductPromotionEntity duplicatePromotion = new ProductPromotionEntity();
        duplicatePromotion.setProductId(duplicateProduct.getId());
        duplicatePromotion.setShopId(duplicateProduct.getShopId());
        duplicatePromotion.setPromotionType(sourcePromotion.getPromotionType());
        duplicatePromotion.setStartsAt(sourcePromotion.getStartsAt());
        duplicatePromotion.setEndsAt(sourcePromotion.getEndsAt());
        duplicatePromotion.setPriorityScore(sourcePromotion.getPriorityScore());
        duplicatePromotion.setPaidAmount(sourcePromotion.getPaidAmount());
        duplicatePromotion.setStatus("DRAFT");
        productPromotionRepository.save(duplicatePromotion);
    }

    private void copyCoupon(Long sourceProductId, ProductEntity duplicateProduct) {
        ProductCouponRuleEntity sourceCoupon = productCouponRuleRepository.findFirstByProductIdOrderByIdDesc(sourceProductId).orElse(null);
        if (sourceCoupon == null) {
            return;
        }
        ProductCouponRuleEntity duplicateCoupon = new ProductCouponRuleEntity();
        duplicateCoupon.setProductId(duplicateProduct.getId());
        duplicateCoupon.setShopId(duplicateProduct.getShopId());
        duplicateCoupon.setCouponCode(sourceCoupon.getCouponCode());
        duplicateCoupon.setCouponTitle(sourceCoupon.getCouponTitle());
        duplicateCoupon.setDiscountType(sourceCoupon.getDiscountType());
        duplicateCoupon.setDiscountValue(sourceCoupon.getDiscountValue());
        duplicateCoupon.setMinOrderAmount(sourceCoupon.getMinOrderAmount());
        duplicateCoupon.setMaxDiscountAmount(sourceCoupon.getMaxDiscountAmount());
        duplicateCoupon.setStartsAt(sourceCoupon.getStartsAt());
        duplicateCoupon.setEndsAt(sourceCoupon.getEndsAt());
        duplicateCoupon.setActive(false);
        productCouponRuleRepository.save(duplicateCoupon);
    }

    private void saveImages(Long productId, ShopCreateProductRequest request, Map<String, Long> variantIdsByClientKey, boolean updateMode) {
        List<ShopProductImageRequest> requestedImages = request.images();
        if (requestedImages != null) {
            productImageRepository.deleteByProductId(productId);
            List<ShopProductImageRequest> normalizedImages = requestedImages.stream()
                    .filter(Objects::nonNull)
                    .toList();
            boolean hasPrimary = normalizedImages.stream().anyMatch(image -> Boolean.TRUE.equals(image.primaryImage()));
            for (int index = 0; index < normalizedImages.size(); index++) {
                ShopProductImageRequest imageRequest = normalizedImages.get(index);
                ProductImageEntity imageEntity = new ProductImageEntity();
                imageEntity.setProductId(productId);
                imageEntity.setVariantId(resolveVariantId(imageRequest.variantClientKey(), variantIdsByClientKey));
                imageEntity.setFileId(imageRequest.fileId());
                imageEntity.setImageRole(resolveImageRole(imageRequest.imageRole(), imageRequest.primaryImage()));
                imageEntity.setSortOrder(imageRequest.sortOrder() == null ? index : imageRequest.sortOrder());
                imageEntity.setPrimaryImage(Boolean.TRUE.equals(imageRequest.primaryImage()) || (!hasPrimary && index == 0));
                productImageRepository.save(imageEntity);
            }
            return;
        }

        if (!updateMode && request.imageFileId() != null) {
            savePrimaryImage(productId, request.imageFileId());
            return;
        }
        if (updateMode && request.imageFileId() != null) {
            savePrimaryImage(productId, request.imageFileId());
        }
    }

    private void savePromotion(ProductEntity productEntity, ShopProductPromotionRequest promotionRequest, boolean updateMode) {
        if (promotionRequest == null) {
            return;
        }
        productPromotionRepository.deleteByProductId(productEntity.getId());
        if (!hasPromotionPayload(promotionRequest)) {
            return;
        }
        ProductPromotionEntity promotionEntity = new ProductPromotionEntity();
        promotionEntity.setProductId(productEntity.getId());
        promotionEntity.setShopId(productEntity.getShopId());
        promotionEntity.setPromotionType(normalizePromotionType(promotionRequest.promotionType()));
        promotionEntity.setStartsAt(requirePromotionDate(promotionRequest.startsAt(), "PROMOTION_START_REQUIRED"));
        promotionEntity.setEndsAt(requirePromotionDate(promotionRequest.endsAt(), "PROMOTION_END_REQUIRED"));
        if (promotionEntity.getEndsAt().isBefore(promotionEntity.getStartsAt())) {
            throw new BusinessException("PROMOTION_DATE_RANGE_INVALID", "Promotion end date must be after start date.", HttpStatus.BAD_REQUEST);
        }
        if (promotionRequest.priorityScore() != null && promotionRequest.priorityScore() < 0) {
            throw new BusinessException("PROMOTION_PRIORITY_INVALID", "Promotion priority score cannot be negative.", HttpStatus.BAD_REQUEST);
        }
        promotionEntity.setPriorityScore(promotionRequest.priorityScore() == null ? 0 : promotionRequest.priorityScore());
        promotionEntity.setPaidAmount(promotionRequest.paidAmount() == null ? BigDecimal.ZERO : promotionRequest.paidAmount());
        promotionEntity.setStatus(normalizePromotionStatus(promotionRequest.status()));
        productPromotionRepository.save(promotionEntity);
    }

    private void saveCoupon(ProductEntity productEntity, ShopProductCouponRequest couponRequest, boolean updateMode) {
        if (couponRequest == null) {
            return;
        }
        productCouponRuleRepository.deleteByProductId(productEntity.getId());
        if (!hasCouponPayload(couponRequest)) {
            return;
        }
        ProductCouponRuleEntity couponRuleEntity = new ProductCouponRuleEntity();
        couponRuleEntity.setProductId(productEntity.getId());
        couponRuleEntity.setShopId(productEntity.getShopId());
        couponRuleEntity.setCouponCode(normalizeRequiredText(couponRequest.couponCode(), "COUPON_CODE_REQUIRED", "Coupon code is required."));
        couponRuleEntity.setCouponTitle(blankToNull(couponRequest.couponTitle()));
        couponRuleEntity.setDiscountType(normalizeCouponDiscountType(couponRequest.discountType()));
        couponRuleEntity.setDiscountValue(requireAmount(couponRequest.discountValue(), "COUPON_DISCOUNT_REQUIRED", "Coupon discount value is required."));
        couponRuleEntity.setMinOrderAmount(couponRequest.minOrderAmount());
        couponRuleEntity.setMaxDiscountAmount(couponRequest.maxDiscountAmount());
        couponRuleEntity.setStartsAt(requireCouponDate(couponRequest.startsAt(), "COUPON_START_REQUIRED"));
        couponRuleEntity.setEndsAt(requireCouponDate(couponRequest.endsAt(), "COUPON_END_REQUIRED"));
        if (couponRuleEntity.getEndsAt().isBefore(couponRuleEntity.getStartsAt())) {
            throw new BusinessException("COUPON_DATE_RANGE_INVALID", "Coupon end date must be after start date.", HttpStatus.BAD_REQUEST);
        }
        if ("PERCENTAGE".equalsIgnoreCase(couponRuleEntity.getDiscountType())
                && couponRuleEntity.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("COUPON_PERCENTAGE_INVALID", "Percentage discount cannot be more than 100.", HttpStatus.BAD_REQUEST);
        }
        couponRuleEntity.setActive(couponRequest.enabled() == null || couponRequest.enabled());
        productCouponRuleRepository.save(couponRuleEntity);
    }

    private Map<Long, ShopCategoryEntity> mappedShopCategories(ShopEntity shopEntity) {
        List<ShopInventoryCategoryEntity> mappings = shopInventoryCategoryRepository.findByShopIdOrderByIdAsc(shopEntity.getId()).stream()
                .filter(ShopInventoryCategoryEntity::isEnabled)
                .toList();
        if (mappings.isEmpty()) {
            return Map.of();
        }
        return shopCategoryRepository.findAllById(mappings.stream().map(ShopInventoryCategoryEntity::getShopCategoryId).toList()).stream()
                .filter(ShopCategoryEntity::isActive)
                .collect(Collectors.toMap(ShopCategoryEntity::getId, Function.identity()));
    }

    private ShopCategoryEntity requireMappedCategory(ShopEntity shopEntity, Long categoryId) {
        if (!shopInventoryCategoryRepository.existsByShopIdAndShopCategoryId(shopEntity.getId(), categoryId)) {
            throw new BusinessException("CATEGORY_NOT_ADDED", "Selected category is not added for this shop.", HttpStatus.BAD_REQUEST);
        }
        return shopCategoryRepository.findById(categoryId)
                .filter(ShopCategoryEntity::isActive)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Selected category does not exist for this shop.", HttpStatus.BAD_REQUEST));
    }

    private List<ShopProductData> buildProductData(
            List<ProductEntity> products,
            Map<Long, ShopCategoryEntity> productCategories,
            Map<String, Long> categoryIdByName,
            ShopProductDeliveryRuleData deliveryRuleData
    ) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(ProductEntity::getId).toList();
        List<ProductVariantEntity> allVariants = productVariantRepository.findByProductIdIn(productIds);
        Map<Long, List<ProductVariantEntity>> variantsByProductId = allVariants.stream()
                .sorted(Comparator.comparing(ProductVariantEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductVariantEntity::getId))
                .collect(Collectors.groupingBy(ProductVariantEntity::getProductId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, InventoryEntity> inventoryByVariantId = inventoryRepository.findByVariantIdIn(allVariants.stream().map(ProductVariantEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(InventoryEntity::getVariantId, Function.identity()));
        Map<Long, List<ProductImageEntity>> imagesByProductId = productImageRepository.findByProductIdIn(productIds).stream()
                .sorted(Comparator.comparing(ProductImageEntity::isPrimaryImage).reversed()
                        .thenComparing(ProductImageEntity::getSortOrder)
                        .thenComparing(ProductImageEntity::getId))
                .collect(Collectors.groupingBy(ProductImageEntity::getProductId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, ProductPromotionEntity> promotionByProductId = productPromotionRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductPromotionEntity::getProductId, Function.identity(), this::pickLatestPromotion));
        Map<Long, ProductCouponRuleEntity> couponByProductId = productCouponRuleRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductCouponRuleEntity::getProductId, Function.identity(), this::pickLatestCoupon));

        return products.stream().map(product -> {
            ShopCategoryEntity productCategoryEntity = productCategories.get(product.getShopCategoryId());
            List<ProductVariantEntity> variantEntities = variantsByProductId.getOrDefault(product.getId(), List.of());
            ProductVariantEntity defaultVariant = variantEntities.stream()
                    .filter(ProductVariantEntity::isDefaultVariant)
                    .findFirst()
                    .orElseGet(() -> variantEntities.isEmpty() ? null : variantEntities.getFirst());
            InventoryEntity inventoryEntity = defaultVariant == null ? null : inventoryByVariantId.get(defaultVariant.getId());
            List<ProductImageEntity> imageEntities = imagesByProductId.getOrDefault(product.getId(), List.of());
            ProductImageEntity primaryImage = imageEntities.stream().filter(ProductImageEntity::isPrimaryImage).findFirst()
                    .orElseGet(() -> imageEntities.isEmpty() ? null : imageEntities.getFirst());
            String categoryName = productCategoryEntity == null ? null : productCategoryEntity.getName();
            Long shopCategoryId = categoryName == null ? null : categoryIdByName.get(normalize(categoryName));
            return new ShopProductData(
                    product.getId(),
                    shopCategoryId,
                    categoryName,
                    product.getName(),
                    product.getShortDescription(),
                    product.getDescription(),
                    product.getBrandName(),
                    defaultVariant == null ? null : defaultVariant.getVariantName(),
                    defaultVariant == null ? null : defaultVariant.getUnitValue(),
                    defaultVariant == null ? null : defaultVariant.getUnitType(),
                    defaultVariant == null ? null : defaultVariant.getWeightInGrams(),
                    defaultVariant == null ? null : defaultVariant.getMrp(),
                    defaultVariant == null ? null : defaultVariant.getSellingPrice(),
                    inventoryEntity == null ? 0 : inventoryEntity.getQuantityAvailable(),
                    inventoryEntity == null ? 0 : inventoryEntity.getReservedQuantity(),
                    inventoryEntity == null ? null : inventoryEntity.getReorderLevel(),
                    inventoryEntity == null ? "OUT_OF_STOCK" : inventoryEntity.getInventoryStatus(),
                    primaryImage == null ? null : primaryImage.getFileId(),
                    product.isActive(),
                    product.isFeatured(),
                    readJsonMap(product.getAttributesJson()),
                    nullSafeDecimal(product.getAvgRating()),
                    zeroIfNull(product.getTotalReviews()),
                    zeroIfNull(product.getTotalOrders()),
                    toPromotionData(promotionByProductId.get(product.getId())),
                    toCouponData(couponByProductId.get(product.getId())),
                    deliveryRuleData,
                    toVariantData(variantEntities, inventoryByVariantId),
                    toImageData(imageEntities),
                    product.getUpdatedAt() == null ? LocalDateTime.now() : product.getUpdatedAt()
            );
        }).toList();
    }

    private List<ShopProductVariantData> toVariantData(List<ProductVariantEntity> variants, Map<Long, InventoryEntity> inventoryByVariantId) {
        return variants.stream().map(variant -> {
            InventoryEntity inventoryEntity = inventoryByVariantId.get(variant.getId());
            Map<String, Object> attributes = readJsonMap(variant.getAttributesJson());
            return new ShopProductVariantData(
                    variant.getId(),
                    variant.getVariantName(),
                    asString(attributes.get("colorName")),
                    asString(attributes.get("colorHex")),
                    variant.getUnitValue(),
                    variant.getUnitType(),
                    variant.getWeightInGrams(),
                    variant.getMrp(),
                    variant.getSellingPrice(),
                    inventoryEntity == null ? 0 : inventoryEntity.getQuantityAvailable(),
                    inventoryEntity == null ? 0 : inventoryEntity.getReservedQuantity(),
                    inventoryEntity == null ? null : inventoryEntity.getReorderLevel(),
                    inventoryEntity == null ? "OUT_OF_STOCK" : inventoryEntity.getInventoryStatus(),
                    variant.isDefaultVariant(),
                    variant.isActive(),
                    variant.getSortOrder(),
                    attributes
            );
        }).toList();
    }

    private List<ShopProductImageData> toImageData(List<ProductImageEntity> images) {
        return images.stream()
                .map(image -> new ShopProductImageData(
                        image.getId(),
                        image.getFileId(),
                        image.getImageRole(),
                        image.getVariantId(),
                        image.getSortOrder(),
                        image.isPrimaryImage()
                ))
                .toList();
    }

    private ShopProductPromotionData toPromotionData(ProductPromotionEntity promotionEntity) {
        if (promotionEntity == null) {
            return null;
        }
        return new ShopProductPromotionData(
                promotionEntity.getId(),
                promotionEntity.getPromotionType(),
                promotionEntity.getStartsAt(),
                promotionEntity.getEndsAt(),
                promotionEntity.getPriorityScore(),
                promotionEntity.getPaidAmount(),
                promotionEntity.getStatus()
        );
    }

    private ShopProductCouponData toCouponData(ProductCouponRuleEntity couponRuleEntity) {
        if (couponRuleEntity == null) {
            return null;
        }
        return new ShopProductCouponData(
                couponRuleEntity.getId(),
                couponRuleEntity.getCouponCode(),
                couponRuleEntity.getCouponTitle(),
                couponRuleEntity.getDiscountType(),
                couponRuleEntity.getDiscountValue(),
                couponRuleEntity.getMinOrderAmount(),
                couponRuleEntity.getMaxDiscountAmount(),
                couponRuleEntity.getStartsAt(),
                couponRuleEntity.getEndsAt(),
                couponRuleEntity.isActive()
        );
    }

    private ShopProductDeliveryRuleData resolveDeliveryRule(Long shopId) {
        return shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shopId)
                .flatMap(location -> shopDeliveryRuleRepository.findByShopLocationId(location.getId())
                        .map(rule -> new ShopProductDeliveryRuleData(
                                location.getId(),
                                rule.getDeliveryType(),
                                rule.getRadiusKm(),
                                rule.getMinOrderAmount(),
                                rule.getDeliveryFee(),
                                rule.getFreeDeliveryAbove(),
                                rule.getOrderCutoffMinutesBeforeClose(),
                                rule.getClosingSoonMinutes()
                        )))
                .orElse(null);
    }

    private ProductPromotionEntity pickLatestPromotion(ProductPromotionEntity left, ProductPromotionEntity right) {
        return left.getId() > right.getId() ? left : right;
    }

    private ProductCouponRuleEntity pickLatestCoupon(ProductCouponRuleEntity left, ProductCouponRuleEntity right) {
        return left.getId() > right.getId() ? left : right;
    }

    private List<ShopProductVariantRequest> normalizeVariantRequests(ShopCreateProductRequest request) {
        if (request.variants() != null && !request.variants().isEmpty()) {
            return request.variants().stream().filter(Objects::nonNull).toList();
        }
        if (request.mrp() == null || request.sellingPrice() == null || request.quantityAvailable() == null) {
            return List.of();
        }
        return List.of(new ShopProductVariantRequest(
                null,
                "default",
                request.variantName(),
                null,
                null,
                request.unitValue(),
                request.unitType(),
                request.weightInGrams(),
                request.mrp(),
                request.sellingPrice(),
                request.quantityAvailable(),
                request.reorderLevel(),
                true,
                true,
                0,
                null
        ));
    }

    private void validateVariantRequest(ShopProductVariantRequest requestedVariant) {
        if (requestedVariant.mrp() == null) {
            throw new BusinessException("MRP_REQUIRED", "Variant MRP is required.", HttpStatus.BAD_REQUEST);
        }
        if (requestedVariant.sellingPrice() == null) {
            throw new BusinessException("SELLING_PRICE_REQUIRED", "Variant selling price is required.", HttpStatus.BAD_REQUEST);
        }
        if (requestedVariant.quantityAvailable() == null) {
            throw new BusinessException("QUANTITY_REQUIRED", "Variant quantity is required.", HttpStatus.BAD_REQUEST);
        }
        if (requestedVariant.sellingPrice().compareTo(requestedVariant.mrp()) > 0) {
            throw new BusinessException("SELLING_PRICE_INVALID", "Selling price cannot be greater than MRP.", HttpStatus.BAD_REQUEST);
        }
        if (requestedVariant.colorHex() != null && !requestedVariant.colorHex().isBlank()) {
            String normalizedHex = requestedVariant.colorHex().trim();
            if (!COLOR_HEX_PATTERN.matcher(normalizedHex).matches()) {
                throw new BusinessException("COLOR_HEX_INVALID", "Color hex must be a valid 6-digit hex code.", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private int resolveDefaultVariantIndex(List<ShopProductVariantRequest> requestedVariants) {
        for (int index = 0; index < requestedVariants.size(); index++) {
            if (Boolean.TRUE.equals(requestedVariants.get(index).defaultVariant())) {
                return index;
            }
        }
        return 0;
    }

    private String resolveVariantName(String itemName, ShopProductVariantRequest requestedVariant, int index) {
        if (requestedVariant.variantName() != null && !requestedVariant.variantName().isBlank()) {
            return normalizeDisplayText(requestedVariant.variantName());
        }
        if (requestedVariant.unitValue() != null && requestedVariant.unitType() != null && !requestedVariant.unitType().isBlank()) {
            return requestedVariant.unitValue().stripTrailingZeros().toPlainString() + " " + requestedVariant.unitType().trim();
        }
        if (requestedVariant.colorName() != null && !requestedVariant.colorName().isBlank()) {
            return normalizeDisplayText(itemName) + " - " + normalizeDisplayText(requestedVariant.colorName());
        }
        return index == 0 ? normalizeDisplayText(itemName) : normalizeDisplayText(itemName) + " Variant " + (index + 1);
    }

    private Long resolveVariantId(String variantClientKey, Map<String, Long> variantIdsByClientKey) {
        if (variantClientKey == null || variantClientKey.isBlank()) {
            return null;
        }
        Long variantId = variantIdsByClientKey.get(variantClientKey.trim());
        if (variantId == null) {
            throw new BusinessException("VARIANT_IMAGE_MAPPING_NOT_FOUND", "Image variant mapping is invalid.", HttpStatus.BAD_REQUEST);
        }
        return variantId;
    }

    private String resolveVariantClientKey(ShopProductVariantRequest requestedVariant, int index) {
        if (requestedVariant.clientKey() != null && !requestedVariant.clientKey().isBlank()) {
            return requestedVariant.clientKey().trim();
        }
        if (requestedVariant.variantId() != null) {
            return "variant-" + requestedVariant.variantId();
        }
        return "variant-index-" + index;
    }

    private Map<String, Object> mergeVariantAttributes(ShopProductVariantRequest requestedVariant) {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        if (requestedVariant.attributes() != null) {
            attributes.putAll(requestedVariant.attributes());
        }
        if (requestedVariant.colorName() != null && !requestedVariant.colorName().isBlank()) {
            attributes.put("colorName", requestedVariant.colorName().trim());
        }
        if (requestedVariant.colorHex() != null && !requestedVariant.colorHex().isBlank()) {
            attributes.put("colorHex", requestedVariant.colorHex().trim());
        }
        return attributes.isEmpty() ? null : attributes;
    }

    private String resolveSku(String requestedSku, Long shopId, Long categoryId, String itemName) {
        if (requestedSku != null && !requestedSku.isBlank()) {
            String normalizedSku = requestedSku.trim();
            if (productRepository.existsBySkuIgnoreCase(normalizedSku)) {
                throw new BusinessException("DUPLICATE_SKU", "SKU already exists.", HttpStatus.BAD_REQUEST);
            }
            return normalizedSku;
        }
        String cleaned = normalize(itemName).replaceAll("[^A-Z0-9]", "");
        String base = cleaned.substring(0, Math.min(6, cleaned.length()));
        if (base.isBlank()) {
            base = "ITEM";
        }
        return "S" + shopId + "C" + categoryId + "-" + base + "-" + System.currentTimeMillis();
    }

    private String resolveDuplicateSku(String sourceSku) {
        String normalizedSourceSku = normalizeRequiredText(sourceSku, "SKU_REQUIRED", "Source SKU is required.");
        String base = normalizedSourceSku + "-COPY";
        String candidate = truncateSku(base);
        int attempt = 2;
        while (productRepository.existsBySkuIgnoreCase(candidate)) {
            candidate = truncateSku(base + "-" + attempt);
            attempt++;
        }
        return candidate;
    }

    private String resolveDuplicateItemName(String sourceName) {
        String normalizedName = normalizeDisplayText(sourceName);
        String suffix = " Copy";
        if (normalizedName.length() + suffix.length() <= 180) {
            return normalizedName + suffix;
        }
        return normalizedName.substring(0, 180 - suffix.length()).trim() + suffix;
    }

    private String truncateSku(String candidate) {
        if (candidate.length() <= 64) {
            return candidate;
        }
        return candidate.substring(0, 64);
    }

    private void savePrimaryImage(Long productId, Long imageFileId) {
        if (imageFileId == null) {
            return;
        }
        ProductImageEntity productImageEntity = productImageRepository.findFirstByProductIdAndPrimaryImageTrue(productId).orElse(null);
        if (productImageEntity == null) {
            productImageEntity = new ProductImageEntity();
            productImageEntity.setProductId(productId);
            productImageEntity.setSortOrder(0);
            productImageEntity.setPrimaryImage(true);
            productImageEntity.setImageRole("COVER");
        }
        productImageEntity.setVariantId(null);
        productImageEntity.setFileId(imageFileId);
        productImageEntity.setImageRole("COVER");
        productImageRepository.save(productImageEntity);
    }

    private String resolveInventoryStatus(Integer quantityAvailable, Integer reorderLevel, boolean active) {
        if (!active) {
            return "DISCONTINUED";
        }
        if (quantityAvailable == null || quantityAvailable <= 0) {
            return "OUT_OF_STOCK";
        }
        if (reorderLevel != null && quantityAvailable <= reorderLevel) {
            return "LOW_STOCK";
        }
        return "IN_STOCK";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String normalizeDisplayText(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("ITEM_NAME_REQUIRED", "Item name is required.", HttpStatus.BAD_REQUEST);
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeRequiredText(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private String resolveProductType(String productType) {
        if (productType == null || productType.isBlank()) {
            return "STANDARD";
        }
        return productType.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveImageRole(String imageRole, Boolean primaryImage) {
        if (imageRole != null && !imageRole.isBlank()) {
            return imageRole.trim().toUpperCase(Locale.ROOT);
        }
        return Boolean.TRUE.equals(primaryImage) ? "COVER" : "GALLERY";
    }

    private String normalizePromotionType(String promotionType) {
        return normalizeRequiredText(
                promotionType == null ? "DEAL" : promotionType,
                "PROMOTION_TYPE_REQUIRED",
                "Promotion type is required."
        ).toUpperCase(Locale.ROOT);
    }

    private String normalizePromotionStatus(String status) {
        if (status == null || status.isBlank()) {
            return "DRAFT";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCouponDiscountType(String discountType) {
        return normalizeRequiredText(
                discountType == null ? "PERCENTAGE" : discountType,
                "COUPON_DISCOUNT_TYPE_REQUIRED",
                "Coupon discount type is required."
        ).toUpperCase(Locale.ROOT);
    }

    private LocalDateTime requirePromotionDate(LocalDateTime value, String code) {
        if (value == null) {
            throw new BusinessException(code, "Promotion start and end time are required.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private LocalDateTime requireCouponDate(LocalDateTime value, String code) {
        if (value == null) {
            throw new BusinessException(code, "Coupon start and end time are required.", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private BigDecimal requireAmount(BigDecimal amount, String code, String message) {
        if (amount == null) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return amount;
    }

    private boolean hasPromotionPayload(ShopProductPromotionRequest promotionRequest) {
        return Boolean.TRUE.equals(promotionRequest.enabled())
                || promotionRequest.promotionType() != null
                || promotionRequest.startsAt() != null
                || promotionRequest.endsAt() != null
                || promotionRequest.paidAmount() != null;
    }

    private boolean hasCouponPayload(ShopProductCouponRequest couponRequest) {
        return Boolean.TRUE.equals(couponRequest.enabled())
                || couponRequest.couponCode() != null
                || couponRequest.discountType() != null
                || couponRequest.discountValue() != null;
    }

    private String writeJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException("JSON_SERIALIZATION_FAILED", "Unable to save product attributes.", HttpStatus.BAD_REQUEST);
        }
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

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nullSafeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record VariantSaveResult(Map<String, Long> variantIdsByClientKey) {
    }
}
