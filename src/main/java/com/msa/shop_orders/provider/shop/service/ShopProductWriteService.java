package com.msa.shop_orders.provider.shop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.InventoryEntity;
import com.msa.shop_orders.persistence.entity.ProductCouponRuleEntity;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.persistence.entity.ProductImageEntity;
import com.msa.shop_orders.persistence.entity.ProductPromotionEntity;
import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import com.msa.shop_orders.persistence.repository.InventoryRepository;
import com.msa.shop_orders.persistence.repository.ProductCouponRuleRepository;
import com.msa.shop_orders.persistence.repository.ProductImageRepository;
import com.msa.shop_orders.persistence.repository.ProductPromotionRepository;
import com.msa.shop_orders.persistence.repository.ProductRepository;
import com.msa.shop_orders.persistence.repository.ProductVariantRepository;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductCouponRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductImageRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductPromotionRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductVariantRequest;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ShopProductWriteService {
    private static final Pattern COLOR_HEX_PATTERN = Pattern.compile("^#?[0-9A-Fa-f]{6}$");

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductPromotionRepository productPromotionRepository;
    private final ProductCouponRuleRepository productCouponRuleRepository;
    private final ObjectMapper objectMapper;
    private final ShopRuntimeSyncService shopRuntimeSyncService;

    public ShopProductWriteService(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            InventoryRepository inventoryRepository,
            ProductImageRepository productImageRepository,
            ProductPromotionRepository productPromotionRepository,
            ProductCouponRuleRepository productCouponRuleRepository,
            ObjectMapper objectMapper,
            ShopRuntimeSyncService shopRuntimeSyncService
    ) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
        this.productImageRepository = productImageRepository;
        this.productPromotionRepository = productPromotionRepository;
        this.productCouponRuleRepository = productCouponRuleRepository;
        this.objectMapper = objectMapper;
        this.shopRuntimeSyncService = shopRuntimeSyncService;
    }

    @Transactional
    public ProductEntity createProduct(ShopShellView shop, ShopCategoryView category, ShopCreateProductRequest request) {
        ProductEntity productEntity = new ProductEntity();
        productEntity.setShopId(shop.getShopId());
        productEntity.setShopCategoryId(category.getCategoryId());
        productEntity.setSku(resolveSku(request.sku(), shop.getShopId(), category.getCategoryId(), request.itemName()));
        populateProduct(productEntity, request);
        productEntity.setAvgRating(BigDecimal.ZERO.setScale(2));
        productEntity.setTotalReviews(0);
        productEntity.setTotalOrders(0);
        productEntity = productRepository.save(productEntity);

        VariantSaveResult variantSaveResult = saveVariants(productEntity, request, false);
        saveImages(productEntity.getId(), request, variantSaveResult.variantIdsByClientKey(), false);
        savePromotion(productEntity, request.promotion());
        saveCoupon(productEntity, request.coupon());
        shopRuntimeSyncService.syncProductAfterCommit(productEntity.getShopId(), productEntity.getId());
        return productEntity;
    }

    @Transactional
    public ProductEntity updateProduct(ShopShellView shop, Long productId, ShopCategoryView category, ShopCreateProductRequest request) {
        ProductEntity productEntity = requireOwnedProduct(productId, shop.getShopId());
        productEntity.setShopCategoryId(category.getCategoryId());
        populateProduct(productEntity, request);
        if (request.sku() != null && !request.sku().isBlank()) {
            String normalizedSku = request.sku().trim();
            if (!normalizedSku.equalsIgnoreCase(productEntity.getSku()) && productRepository.existsBySkuIgnoreCase(normalizedSku)) {
                throw new BusinessException("DUPLICATE_SKU", "SKU already exists.", HttpStatus.BAD_REQUEST);
            }
            productEntity.setSku(normalizedSku);
        }
        productEntity = productRepository.save(productEntity);

        VariantSaveResult variantSaveResult = saveVariants(productEntity, request, true);
        saveImages(productEntity.getId(), request, variantSaveResult.variantIdsByClientKey(), true);
        savePromotion(productEntity, request.promotion());
        saveCoupon(productEntity, request.coupon());
        shopRuntimeSyncService.syncProductAfterCommit(productEntity.getShopId(), productEntity.getId());
        return productEntity;
    }

    @Transactional
    public ProductEntity duplicateProduct(ShopShellView shop, Long productId) {
        ProductEntity sourceProduct = requireOwnedProduct(productId, shop.getShopId());

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
        shopRuntimeSyncService.syncProductAfterCommit(duplicateProduct.getShopId(), duplicateProduct.getId());
        return duplicateProduct;
    }

    @Transactional
    public ProductEntity updateProductStatus(Long productId, boolean active) {
        ProductEntity productEntity = requireProduct(productId);
        productEntity.setActive(active);
        if (!active) {
            productEntity.setFeatured(false);
        }
        ProductEntity saved = productRepository.save(productEntity);
        updateVariantInventoryStatuses(saved);
        shopRuntimeSyncService.syncProductAfterCommit(saved.getShopId(), saved.getId());
        return saved;
    }

    @Transactional
    public ProductEntity updateProductFeatured(Long productId, boolean featured) {
        ProductEntity product = requireProduct(productId);
        product.setFeatured(featured);
        ProductEntity saved = productRepository.save(product);
        shopRuntimeSyncService.syncProductAfterCommit(saved.getShopId(), saved.getId());
        return saved;
    }

    @Transactional
    public ProductEntity updatePromotionStatus(Long productId, String status) {
        ProductEntity product = requireProduct(productId);
        ProductPromotionEntity promotion = productPromotionRepository.findFirstByProductIdOrderByIdDesc(productId)
                .orElseThrow(() -> new BusinessException("PROMOTION_NOT_FOUND", "Promotion not found.", HttpStatus.NOT_FOUND));
        promotion.setStatus(status == null ? null : status.trim().toUpperCase());
        productPromotionRepository.save(promotion);
        shopRuntimeSyncService.syncProductAfterCommit(product.getShopId(), product.getId());
        return product;
    }

    @Transactional
    public ProductEntity updateCouponStatus(Long productId, boolean active) {
        ProductEntity product = requireProduct(productId);
        ProductCouponRuleEntity coupon = productCouponRuleRepository.findFirstByProductIdOrderByIdDesc(productId)
                .orElseThrow(() -> new BusinessException("COUPON_NOT_FOUND", "Coupon not found.", HttpStatus.NOT_FOUND));
        coupon.setActive(active);
        productCouponRuleRepository.save(coupon);
        shopRuntimeSyncService.syncProductAfterCommit(product.getShopId(), product.getId());
        return product;
    }

    private ProductEntity requireOwnedProduct(Long productId, Long shopId) {
        return productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
    }

    private ProductEntity requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found.", HttpStatus.NOT_FOUND));
    }

    private void populateProduct(ProductEntity productEntity, ShopCreateProductRequest request) {
        productEntity.setName(normalizeDisplayText(request.itemName()));
        productEntity.setShortDescription(blankToNull(request.shortDescription()));
        productEntity.setDescription(blankToNull(request.description()));
        productEntity.setProductType(resolveProductType(request.productType()));
        productEntity.setBrandName(blankToNull(request.brandName()));
        productEntity.setAttributesJson(writeJson(request.attributes()));
        productEntity.setRequiresPrescription(Boolean.TRUE.equals(request.requiresPrescription()));
        productEntity.setActive(request.active() == null || request.active());
        productEntity.setFeatured(Boolean.TRUE.equals(request.featured()));
    }

    private void updateVariantInventoryStatuses(ProductEntity productEntity) {
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

    private void savePromotion(ProductEntity productEntity, ShopProductPromotionRequest promotionRequest) {
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

    private void saveCoupon(ProductEntity productEntity, ShopProductCouponRequest couponRequest) {
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

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
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
