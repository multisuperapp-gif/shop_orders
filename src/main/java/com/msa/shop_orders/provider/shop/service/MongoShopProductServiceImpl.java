package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.ShopCategoryEntity;
import com.msa.shop_orders.persistence.entity.ShopDeliveryRuleEntity;
import com.msa.shop_orders.persistence.entity.ShopEntity;
import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import com.msa.shop_orders.persistence.entity.ShopInventoryCategoryEntity;
import com.msa.shop_orders.persistence.mongo.document.ShopProductDocument;
import com.msa.shop_orders.persistence.mongo.repository.ShopProductMongoQueryRepository;
import com.msa.shop_orders.persistence.mongo.repository.ShopProductMongoRepository;
import com.msa.shop_orders.persistence.repository.ShopCategoryRepository;
import com.msa.shop_orders.persistence.repository.ShopDeliveryRuleRepository;
import com.msa.shop_orders.persistence.repository.ShopInventoryCategoryRepository;
import com.msa.shop_orders.persistence.repository.ShopLocationRepository;
import com.msa.shop_orders.provider.shop.dto.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app.mongodb", name = "enabled", havingValue = "true")
public class MongoShopProductServiceImpl implements ShopProductService {
    private static final Pattern COLOR_HEX_PATTERN = Pattern.compile("^#?[0-9A-Fa-f]{6}$");

    private final ShopContextService shopContextService;
    private final ShopInventoryCategoryRepository shopInventoryCategoryRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final ShopLocationRepository shopLocationRepository;
    private final ShopDeliveryRuleRepository shopDeliveryRuleRepository;
    private final ShopProductMongoRepository productMongoRepository;
    private final ShopProductMongoQueryRepository productMongoQueryRepository;
    private final MongoSequenceService sequenceService;
    private final ShopProductActivityLogService activityLogService;

    public MongoShopProductServiceImpl(
            ShopContextService shopContextService,
            ShopInventoryCategoryRepository shopInventoryCategoryRepository,
            ShopCategoryRepository shopCategoryRepository,
            ShopLocationRepository shopLocationRepository,
            ShopDeliveryRuleRepository shopDeliveryRuleRepository,
            ShopProductMongoRepository productMongoRepository,
            ShopProductMongoQueryRepository productMongoQueryRepository,
            MongoSequenceService sequenceService,
            ShopProductActivityLogService activityLogService
    ) {
        this.shopContextService = shopContextService;
        this.shopInventoryCategoryRepository = shopInventoryCategoryRepository;
        this.shopCategoryRepository = shopCategoryRepository;
        this.shopLocationRepository = shopLocationRepository;
        this.shopDeliveryRuleRepository = shopDeliveryRuleRepository;
        this.productMongoRepository = productMongoRepository;
        this.productMongoQueryRepository = productMongoQueryRepository;
        this.sequenceService = sequenceService;
        this.activityLogService = activityLogService;
    }

    @Override
    public List<ShopProductData> products(Long categoryId) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        Map<Long, ShopCategoryEntity> mappedCategories = mappedShopCategories(shopEntity);
        if (categoryId != null && !mappedCategories.containsKey(categoryId)) {
            throw new BusinessException("CATEGORY_NOT_ADDED", "Selected category is not added for this shop.", HttpStatus.BAD_REQUEST);
        }
        List<ShopProductDocument> products = categoryId == null
                ? productMongoRepository.findByShopIdOrderByUpdatedAtDesc(shopEntity.getId())
                : productMongoRepository.findByShopIdAndShopCategoryIdOrderByUpdatedAtDesc(shopEntity.getId(), categoryId);
        return toProductData(products, mappedCategories, resolveDeliveryRule(shopEntity.getId()));
    }

    @Override
    public ShopProductData createProduct(ShopCreateProductRequest request) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ShopCategoryEntity category = requireMappedCategory(shopEntity, request.categoryId());
        LocalDateTime now = LocalDateTime.now();

        ShopProductDocument document = new ShopProductDocument();
        document.setProductId(sequenceService.next("products"));
        document.setShopId(shopEntity.getId());
        document.setShopCategoryId(category.getId());
        document.setSku(resolveSku(request.sku(), shopEntity.getId(), category.getId(), request.itemName()));
        applyRequest(document, request);
        document.setAvgRating(BigDecimal.ZERO.setScale(2));
        document.setTotalReviews(0);
        document.setTotalOrders(0);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        productMongoRepository.save(document);
        activityLogService.mongoProductCreated(shopEntity, document.getProductId(), document.getName());
        return loadSavedProduct(shopEntity, document.getProductId());
    }

    @Override
    public ShopProductData updateProduct(Long productId, ShopCreateProductRequest request) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ShopProductDocument document = productMongoRepository.findByShopIdAndProductId(shopEntity.getId(), productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
        ShopCategoryEntity category = requireMappedCategory(shopEntity, request.categoryId());
        document.setShopCategoryId(category.getId());
        if (request.sku() != null && !request.sku().isBlank()) {
            String normalizedSku = request.sku().trim();
            if (!normalizedSku.equalsIgnoreCase(document.getSku()) && productMongoQueryRepository.existsBySkuIgnoreCase(normalizedSku)) {
                throw new BusinessException("DUPLICATE_SKU", "SKU already exists.", HttpStatus.BAD_REQUEST);
            }
            document.setSku(normalizedSku);
        }
        applyRequest(document, request);
        document.setUpdatedAt(LocalDateTime.now());
        productMongoRepository.save(document);
        activityLogService.mongoProductUpdated(shopEntity, document.getProductId(), document.getName());
        return loadSavedProduct(shopEntity, document.getProductId());
    }

    @Override
    public ShopProductData duplicateProduct(Long productId) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ShopProductDocument source = productMongoRepository.findByShopIdAndProductId(shopEntity.getId(), productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
        requireMappedCategory(shopEntity, source.getShopCategoryId());
        LocalDateTime now = LocalDateTime.now();

        ShopProductDocument duplicate = new ShopProductDocument();
        duplicate.setProductId(sequenceService.next("products"));
        duplicate.setShopId(source.getShopId());
        duplicate.setShopCategoryId(source.getShopCategoryId());
        duplicate.setSku(resolveDuplicateSku(source.getSku()));
        duplicate.setName(resolveDuplicateItemName(source.getName()));
        duplicate.setShortDescription(source.getShortDescription());
        duplicate.setDescription(source.getDescription());
        duplicate.setProductType(source.getProductType());
        duplicate.setBrandName(source.getBrandName());
        duplicate.setAttributes(copyMap(source.getAttributes()));
        duplicate.setRequiresPrescription(source.isRequiresPrescription());
        duplicate.setAvgRating(BigDecimal.ZERO.setScale(2));
        duplicate.setTotalReviews(0);
        duplicate.setTotalOrders(0);
        duplicate.setActive(false);
        duplicate.setFeatured(false);
        List<ShopProductDocument.Variant> duplicateVariants = copyVariants(source.getVariants(), false);
        duplicate.setVariants(duplicateVariants);
        duplicate.setImages(copyImages(source.getImages(), buildVariantIdMap(source.getVariants(), duplicateVariants)));
        duplicate.setPromotion(copyPromotion(source.getPromotion()));
        duplicate.setCoupon(copyCoupon(source.getCoupon()));
        duplicate.setCreatedAt(now);
        duplicate.setUpdatedAt(now);
        productMongoRepository.save(duplicate);
        activityLogService.mongoProductDuplicated(shopEntity, source.getProductId(), duplicate.getProductId(), duplicate.getName());
        return loadSavedProduct(shopEntity, duplicate.getProductId());
    }

    @Override
    public ShopProductData updateProductStatus(Long productId, ShopProductStatusUpdateRequest request) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ShopProductDocument document = productMongoRepository.findByShopIdAndProductId(shopEntity.getId(), productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
        boolean active = Boolean.TRUE.equals(request.active());
        document.setActive(active);
        if (!active) {
            document.setFeatured(false);
        }
        for (ShopProductDocument.Variant variant : safeList(document.getVariants())) {
            if (variant.getInventory() == null) {
                variant.setInventory(new ShopProductDocument.Inventory());
            }
            variant.getInventory().setInventoryStatus(resolveInventoryStatus(
                    variant.getInventory().getQuantityAvailable(),
                    variant.getInventory().getReorderLevel(),
                    active && variant.isActive()
            ));
        }
        document.setUpdatedAt(LocalDateTime.now());
        productMongoRepository.save(document);
        activityLogService.mongoProductStatusChanged(shopEntity, document.getProductId(), document.getName(), active);
        return loadSavedProduct(shopEntity, document.getProductId());
    }

    @Override
    public void removeProduct(Long productId) {
        updateProductStatus(productId, new ShopProductStatusUpdateRequest(false));
    }

    @Override
    public List<ShopProductActivityData> productActivity(Long productId) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        productMongoRepository.findByShopIdAndProductId(shopEntity.getId(), productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
        return activityLogService.productActivity(shopEntity.getId(), productId);
    }

    private void applyRequest(ShopProductDocument document, ShopCreateProductRequest request) {
        document.setName(normalizeDisplayText(request.itemName()));
        document.setShortDescription(blankToNull(request.shortDescription()));
        document.setDescription(blankToNull(request.description()));
        document.setProductType(resolveProductType(request.productType()));
        document.setBrandName(blankToNull(request.brandName()));
        document.setAttributes(copyMap(request.attributes()));
        document.setRequiresPrescription(Boolean.TRUE.equals(request.requiresPrescription()));
        document.setActive(request.active() == null || request.active());
        document.setFeatured(Boolean.TRUE.equals(request.featured()));
        List<ShopProductDocument.Variant> variants = buildVariants(document, request);
        document.setVariants(variants);
        document.setImages(buildImages(document, request, variants));
        document.setPromotion(buildPromotion(request.promotion()));
        document.setCoupon(buildCoupon(request.coupon()));
    }

    private List<ShopProductDocument.Variant> buildVariants(ShopProductDocument document, ShopCreateProductRequest request) {
        List<ShopProductVariantRequest> requestedVariants = normalizeVariantRequests(request);
        if (requestedVariants.isEmpty()) {
            throw new BusinessException("PRODUCT_VARIANT_REQUIRED", "At least one product variant is required.", HttpStatus.BAD_REQUEST);
        }
        Map<Long, ShopProductDocument.Variant> existingById = safeList(document.getVariants()).stream()
                .filter(variant -> variant.getVariantId() != null)
                .collect(Collectors.toMap(ShopProductDocument.Variant::getVariantId, Function.identity(), (left, right) -> left));
        int defaultIndex = resolveDefaultVariantIndex(requestedVariants);
        List<ShopProductDocument.Variant> variants = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < requestedVariants.size(); index++) {
            ShopProductVariantRequest requestedVariant = requestedVariants.get(index);
            validateVariantRequest(requestedVariant);
            ShopProductDocument.Variant variant = requestedVariant.variantId() == null
                    ? new ShopProductDocument.Variant()
                    : copyVariant(existingById.get(requestedVariant.variantId()), true);
            if (variant == null) {
                variant = new ShopProductDocument.Variant();
            }
            if (variant.getVariantId() == null) {
                variant.setVariantId(sequenceService.next("product_variants"));
            }
            variant.setVariantName(resolveVariantName(document.getName(), requestedVariant, index));
            String nameKey = normalize(variant.getVariantName());
            if (!names.add(nameKey)) {
                throw new BusinessException("DUPLICATE_VARIANT", "Variant names must be unique within an item.", HttpStatus.BAD_REQUEST);
            }
            variant.setAttributes(mergeVariantAttributes(requestedVariant));
            variant.setUnitValue(requestedVariant.unitValue());
            variant.setUnitType(blankToNull(requestedVariant.unitType()));
            variant.setWeightInGrams(requestedVariant.weightInGrams());
            variant.setMrp(requestedVariant.mrp());
            variant.setSellingPrice(requestedVariant.sellingPrice());
            variant.setDefaultVariant(index == defaultIndex);
            variant.setSortOrder(requestedVariant.sortOrder() == null ? index : requestedVariant.sortOrder());
            variant.setActive(requestedVariant.active() == null || requestedVariant.active());
            ShopProductDocument.Inventory inventory = variant.getInventory() == null ? new ShopProductDocument.Inventory() : variant.getInventory();
            inventory.setQuantityAvailable(zeroIfNull(requestedVariant.quantityAvailable()));
            inventory.setReservedQuantity(zeroIfNull(inventory.getReservedQuantity()));
            inventory.setReorderLevel(requestedVariant.reorderLevel());
            inventory.setInventoryStatus(resolveInventoryStatus(
                    requestedVariant.quantityAvailable(),
                    requestedVariant.reorderLevel(),
                    document.isActive() && variant.isActive()
            ));
            variant.setInventory(inventory);
            variants.add(variant);
        }
        for (ShopProductDocument.Variant existing : existingById.values()) {
            boolean stillPresent = variants.stream().anyMatch(variant -> Objects.equals(variant.getVariantId(), existing.getVariantId()));
            if (!stillPresent) {
                ShopProductDocument.Variant disabled = copyVariant(existing, true);
                disabled.setDefaultVariant(false);
                disabled.setActive(false);
                if (disabled.getInventory() != null) {
                    disabled.getInventory().setInventoryStatus(resolveInventoryStatus(
                            disabled.getInventory().getQuantityAvailable(),
                            disabled.getInventory().getReorderLevel(),
                            false
                    ));
                }
                variants.add(disabled);
            }
        }
        variants.sort(Comparator.comparing(ShopProductDocument.Variant::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ShopProductDocument.Variant::getVariantId));
        return variants;
    }

    private List<ShopProductDocument.Image> buildImages(
            ShopProductDocument document,
            ShopCreateProductRequest request,
            List<ShopProductDocument.Variant> variants
    ) {
        Map<String, Long> variantIdsByClientKey = resolveVariantClientKeys(request, variants);
        if (request.images() != null) {
            List<ShopProductImageRequest> normalizedImages = request.images().stream().filter(Objects::nonNull).toList();
            boolean hasPrimary = normalizedImages.stream().anyMatch(image -> Boolean.TRUE.equals(image.primaryImage()));
            List<ShopProductDocument.Image> images = new ArrayList<>();
            for (int index = 0; index < normalizedImages.size(); index++) {
                ShopProductImageRequest imageRequest = normalizedImages.get(index);
                ShopProductDocument.Image image = new ShopProductDocument.Image();
                image.setImageId(sequenceService.next("product_images"));
                image.setVariantId(resolveVariantId(imageRequest.variantClientKey(), variantIdsByClientKey));
                image.setFileId(imageRequest.fileId());
                image.setImageRole(resolveImageRole(imageRequest.imageRole(), imageRequest.primaryImage()));
                image.setSortOrder(imageRequest.sortOrder() == null ? index : imageRequest.sortOrder());
                image.setPrimaryImage(Boolean.TRUE.equals(imageRequest.primaryImage()) || (!hasPrimary && index == 0));
                images.add(image);
            }
            return images;
        }
        if (request.imageFileId() != null) {
            ShopProductDocument.Image image = safeList(document.getImages()).stream()
                    .filter(ShopProductDocument.Image::isPrimaryImage)
                    .findFirst()
                    .orElseGet(ShopProductDocument.Image::new);
            if (image.getImageId() == null) {
                image.setImageId(sequenceService.next("product_images"));
            }
            image.setVariantId(null);
            image.setFileId(request.imageFileId());
            image.setImageRole("COVER");
            image.setSortOrder(0);
            image.setPrimaryImage(true);
            return List.of(image);
        }
        return safeList(document.getImages());
    }

    private Map<String, Long> resolveVariantClientKeys(ShopCreateProductRequest request, List<ShopProductDocument.Variant> variants) {
        List<ShopProductVariantRequest> requestedVariants = normalizeVariantRequests(request);
        Map<String, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < requestedVariants.size() && index < variants.size(); index++) {
            result.put(resolveVariantClientKey(requestedVariants.get(index), index), variants.get(index).getVariantId());
        }
        return result;
    }

    private ShopProductDocument.Promotion buildPromotion(ShopProductPromotionRequest request) {
        if (request == null || !hasPromotionPayload(request)) {
            return null;
        }
        ShopProductDocument.Promotion promotion = new ShopProductDocument.Promotion();
        promotion.setPromotionId(sequenceService.next("product_promotions"));
        promotion.setPromotionType(normalizePromotionType(request.promotionType()));
        promotion.setStartsAt(requirePromotionDate(request.startsAt(), "PROMOTION_START_REQUIRED"));
        promotion.setEndsAt(requirePromotionDate(request.endsAt(), "PROMOTION_END_REQUIRED"));
        if (promotion.getEndsAt().isBefore(promotion.getStartsAt())) {
            throw new BusinessException("PROMOTION_DATE_RANGE_INVALID", "Promotion end date must be after start date.", HttpStatus.BAD_REQUEST);
        }
        if (request.priorityScore() != null && request.priorityScore() < 0) {
            throw new BusinessException("PROMOTION_PRIORITY_INVALID", "Promotion priority score cannot be negative.", HttpStatus.BAD_REQUEST);
        }
        promotion.setPriorityScore(request.priorityScore() == null ? 0 : request.priorityScore());
        promotion.setPaidAmount(request.paidAmount() == null ? BigDecimal.ZERO : request.paidAmount());
        promotion.setStatus(normalizePromotionStatus(request.status()));
        return promotion;
    }

    private ShopProductDocument.Coupon buildCoupon(ShopProductCouponRequest request) {
        if (request == null || !hasCouponPayload(request)) {
            return null;
        }
        ShopProductDocument.Coupon coupon = new ShopProductDocument.Coupon();
        coupon.setCouponId(sequenceService.next("product_coupon_rules"));
        coupon.setCouponCode(normalizeRequiredText(request.couponCode(), "COUPON_CODE_REQUIRED", "Coupon code is required."));
        coupon.setCouponTitle(blankToNull(request.couponTitle()));
        coupon.setDiscountType(normalizeCouponDiscountType(request.discountType()));
        coupon.setDiscountValue(requireAmount(request.discountValue(), "COUPON_DISCOUNT_REQUIRED", "Coupon discount value is required."));
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setMaxDiscountAmount(request.maxDiscountAmount());
        coupon.setStartsAt(requireCouponDate(request.startsAt(), "COUPON_START_REQUIRED"));
        coupon.setEndsAt(requireCouponDate(request.endsAt(), "COUPON_END_REQUIRED"));
        if (coupon.getEndsAt().isBefore(coupon.getStartsAt())) {
            throw new BusinessException("COUPON_DATE_RANGE_INVALID", "Coupon end date must be after start date.", HttpStatus.BAD_REQUEST);
        }
        if ("PERCENTAGE".equalsIgnoreCase(coupon.getDiscountType())
                && coupon.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("COUPON_PERCENTAGE_INVALID", "Percentage discount cannot be more than 100.", HttpStatus.BAD_REQUEST);
        }
        coupon.setActive(request.enabled() == null || request.enabled());
        return coupon;
    }

    private ShopProductData loadSavedProduct(ShopEntity shopEntity, Long productId) {
        Map<Long, ShopCategoryEntity> mappedCategories = mappedShopCategories(shopEntity);
        ShopProductDocument product = productMongoRepository.findByShopIdAndProductId(shopEntity.getId(), productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_SAVE_FAILED", "Unable to load saved product.", HttpStatus.INTERNAL_SERVER_ERROR));
        return toProductData(List.of(product), mappedCategories, resolveDeliveryRule(shopEntity.getId())).getFirst();
    }

    private List<ShopProductData> toProductData(
            List<ShopProductDocument> products,
            Map<Long, ShopCategoryEntity> categories,
            ShopProductDeliveryRuleData deliveryRule
    ) {
        return products.stream().map(product -> {
            ShopCategoryEntity category = categories.get(product.getShopCategoryId());
            List<ShopProductDocument.Variant> variants = safeList(product.getVariants());
            ShopProductDocument.Variant defaultVariant = variants.stream()
                    .filter(ShopProductDocument.Variant::isDefaultVariant)
                    .findFirst()
                    .orElseGet(() -> variants.isEmpty() ? null : variants.getFirst());
            ShopProductDocument.Inventory inventory = defaultVariant == null ? null : defaultVariant.getInventory();
            List<ShopProductDocument.Image> images = safeList(product.getImages()).stream()
                    .sorted(Comparator.comparing(ShopProductDocument.Image::isPrimaryImage).reversed()
                            .thenComparing(ShopProductDocument.Image::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(ShopProductDocument.Image::getImageId, Comparator.nullsLast(Long::compareTo)))
                    .toList();
            ShopProductDocument.Image primaryImage = images.stream().filter(ShopProductDocument.Image::isPrimaryImage).findFirst()
                    .orElseGet(() -> images.isEmpty() ? null : images.getFirst());
            return new ShopProductData(
                    product.getProductId(),
                    product.getShopCategoryId(),
                    category == null ? null : category.getName(),
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
                    inventory == null ? 0 : zeroIfNull(inventory.getQuantityAvailable()),
                    inventory == null ? 0 : zeroIfNull(inventory.getReservedQuantity()),
                    inventory == null ? null : inventory.getReorderLevel(),
                    inventory == null ? "OUT_OF_STOCK" : inventory.getInventoryStatus(),
                    primaryImage == null ? null : primaryImage.getFileId(),
                    product.isActive(),
                    product.isFeatured(),
                    emptyIfNull(product.getAttributes()),
                    nullSafeDecimal(product.getAvgRating()),
                    zeroIfNull(product.getTotalReviews()),
                    zeroIfNull(product.getTotalOrders()),
                    toPromotionData(product.getPromotion()),
                    toCouponData(product.getCoupon()),
                    deliveryRule,
                    toVariantData(variants),
                    toImageData(images),
                    product.getUpdatedAt() == null ? LocalDateTime.now() : product.getUpdatedAt()
            );
        }).toList();
    }

    private List<ShopProductVariantData> toVariantData(List<ShopProductDocument.Variant> variants) {
        return variants.stream().map(variant -> {
            ShopProductDocument.Inventory inventory = variant.getInventory();
            Map<String, Object> attributes = emptyIfNull(variant.getAttributes());
            return new ShopProductVariantData(
                    variant.getVariantId(),
                    variant.getVariantName(),
                    asString(attributes.get("colorName")),
                    asString(attributes.get("colorHex")),
                    variant.getUnitValue(),
                    variant.getUnitType(),
                    variant.getWeightInGrams(),
                    variant.getMrp(),
                    variant.getSellingPrice(),
                    inventory == null ? 0 : zeroIfNull(inventory.getQuantityAvailable()),
                    inventory == null ? 0 : zeroIfNull(inventory.getReservedQuantity()),
                    inventory == null ? null : inventory.getReorderLevel(),
                    inventory == null ? "OUT_OF_STOCK" : inventory.getInventoryStatus(),
                    variant.isDefaultVariant(),
                    variant.isActive(),
                    variant.getSortOrder(),
                    attributes
            );
        }).toList();
    }

    private List<ShopProductImageData> toImageData(List<ShopProductDocument.Image> images) {
        return images.stream()
                .map(image -> new ShopProductImageData(
                        image.getImageId(),
                        image.getFileId(),
                        image.getImageRole(),
                        image.getVariantId(),
                        image.getSortOrder(),
                        image.isPrimaryImage()
                ))
                .toList();
    }

    private ShopProductPromotionData toPromotionData(ShopProductDocument.Promotion promotion) {
        if (promotion == null) {
            return null;
        }
        return new ShopProductPromotionData(
                promotion.getPromotionId(),
                promotion.getPromotionType(),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.getPriorityScore(),
                promotion.getPaidAmount(),
                promotion.getStatus()
        );
    }

    private ShopProductCouponData toCouponData(ShopProductDocument.Coupon coupon) {
        if (coupon == null) {
            return null;
        }
        return new ShopProductCouponData(
                coupon.getCouponId(),
                coupon.getCouponCode(),
                coupon.getCouponTitle(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getMaxDiscountAmount(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                coupon.isActive()
        );
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

    private ShopProductDeliveryRuleData resolveDeliveryRule(Long shopId) {
        return shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shopId)
                .flatMap(location -> shopDeliveryRuleRepository.findByShopLocationId(location.getId())
                        .map(rule -> toDeliveryRule(location, rule)))
                .orElse(null);
    }

    private ShopProductDeliveryRuleData toDeliveryRule(ShopLocationEntity location, ShopDeliveryRuleEntity rule) {
        return new ShopProductDeliveryRuleData(
                location.getId(),
                rule.getDeliveryType(),
                rule.getRadiusKm(),
                rule.getMinOrderAmount(),
                rule.getDeliveryFee(),
                rule.getFreeDeliveryAbove(),
                rule.getOrderCutoffMinutesBeforeClose(),
                rule.getClosingSoonMinutes()
        );
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
        if (requestedVariant.colorHex() != null && !requestedVariant.colorHex().isBlank()
                && !COLOR_HEX_PATTERN.matcher(requestedVariant.colorHex().trim()).matches()) {
            throw new BusinessException("COLOR_HEX_INVALID", "Color hex must be a valid 6-digit hex code.", HttpStatus.BAD_REQUEST);
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
            if (productMongoQueryRepository.existsBySkuIgnoreCase(normalizedSku)) {
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
        while (productMongoQueryRepository.existsBySkuIgnoreCase(candidate)) {
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
        return candidate.length() <= 64 ? candidate : candidate.substring(0, 64);
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
        return productType == null || productType.isBlank() ? "STANDARD" : productType.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveImageRole(String imageRole, Boolean primaryImage) {
        return imageRole != null && !imageRole.isBlank()
                ? imageRole.trim().toUpperCase(Locale.ROOT)
                : Boolean.TRUE.equals(primaryImage) ? "COVER" : "GALLERY";
    }

    private String normalizePromotionType(String promotionType) {
        return normalizeRequiredText(promotionType == null ? "DEAL" : promotionType, "PROMOTION_TYPE_REQUIRED", "Promotion type is required.")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizePromotionStatus(String status) {
        return status == null || status.isBlank() ? "DRAFT" : status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCouponDiscountType(String discountType) {
        return normalizeRequiredText(discountType == null ? "PERCENTAGE" : discountType, "COUPON_DISCOUNT_TYPE_REQUIRED", "Coupon discount type is required.")
                .toUpperCase(Locale.ROOT);
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

    private boolean hasPromotionPayload(ShopProductPromotionRequest request) {
        return Boolean.TRUE.equals(request.enabled())
                || request.promotionType() != null
                || request.startsAt() != null
                || request.endsAt() != null
                || request.paidAmount() != null;
    }

    private boolean hasCouponPayload(ShopProductCouponRequest request) {
        return Boolean.TRUE.equals(request.enabled())
                || request.couponCode() != null
                || request.discountType() != null
                || request.discountValue() != null;
    }

    private List<ShopProductDocument.Variant> copyVariants(List<ShopProductDocument.Variant> source, boolean keepIds) {
        return safeList(source).stream()
                .map(variant -> copyVariant(variant, keepIds))
                .toList();
    }

    private ShopProductDocument.Variant copyVariant(ShopProductDocument.Variant source, boolean keepId) {
        if (source == null) {
            return null;
        }
        ShopProductDocument.Variant copy = new ShopProductDocument.Variant();
        copy.setVariantId(keepId ? source.getVariantId() : sequenceService.next("product_variants"));
        copy.setVariantName(source.getVariantName());
        copy.setAttributes(copyMap(source.getAttributes()));
        copy.setUnitValue(source.getUnitValue());
        copy.setUnitType(source.getUnitType());
        copy.setWeightInGrams(source.getWeightInGrams());
        copy.setMrp(source.getMrp());
        copy.setSellingPrice(source.getSellingPrice());
        copy.setDefaultVariant(source.isDefaultVariant());
        copy.setSortOrder(source.getSortOrder());
        copy.setActive(source.isActive());
        if (source.getInventory() != null) {
            ShopProductDocument.Inventory inventory = new ShopProductDocument.Inventory();
            inventory.setQuantityAvailable(zeroIfNull(source.getInventory().getQuantityAvailable()));
            inventory.setReservedQuantity(0);
            inventory.setReorderLevel(source.getInventory().getReorderLevel());
            inventory.setInventoryStatus(resolveInventoryStatus(
                    inventory.getQuantityAvailable(),
                    inventory.getReorderLevel(),
                    copy.isActive()
            ));
            copy.setInventory(inventory);
        }
        return copy;
    }

    private Map<Long, Long> buildVariantIdMap(List<ShopProductDocument.Variant> source, List<ShopProductDocument.Variant> copiedVariants) {
        Map<Long, Long> variantIdMap = new LinkedHashMap<>();
        List<ShopProductDocument.Variant> sourceVariants = safeList(source);
        List<ShopProductDocument.Variant> duplicatedVariants = safeList(copiedVariants);
        for (int index = 0; index < sourceVariants.size() && index < duplicatedVariants.size(); index++) {
            Long sourceVariantId = sourceVariants.get(index).getVariantId();
            Long duplicateVariantId = duplicatedVariants.get(index).getVariantId();
            if (sourceVariantId != null && duplicateVariantId != null) {
                variantIdMap.put(sourceVariantId, duplicateVariantId);
            }
        }
        return variantIdMap;
    }

    private List<ShopProductDocument.Image> copyImages(List<ShopProductDocument.Image> source, Map<Long, Long> variantIdMap) {
        return safeList(source).stream().map(image -> {
            ShopProductDocument.Image copy = new ShopProductDocument.Image();
            copy.setImageId(sequenceService.next("product_images"));
            copy.setVariantId(image.getVariantId() == null ? null : variantIdMap.getOrDefault(image.getVariantId(), image.getVariantId()));
            copy.setFileId(image.getFileId());
            copy.setImageRole(image.getImageRole());
            copy.setSortOrder(image.getSortOrder());
            copy.setPrimaryImage(image.isPrimaryImage());
            return copy;
        }).toList();
    }

    private ShopProductDocument.Promotion copyPromotion(ShopProductDocument.Promotion source) {
        if (source == null) {
            return null;
        }
        ShopProductDocument.Promotion copy = new ShopProductDocument.Promotion();
        copy.setPromotionId(sequenceService.next("product_promotions"));
        copy.setPromotionType(source.getPromotionType());
        copy.setStartsAt(source.getStartsAt());
        copy.setEndsAt(source.getEndsAt());
        copy.setPriorityScore(source.getPriorityScore());
        copy.setPaidAmount(source.getPaidAmount());
        copy.setStatus("DRAFT");
        return copy;
    }

    private ShopProductDocument.Coupon copyCoupon(ShopProductDocument.Coupon source) {
        if (source == null) {
            return null;
        }
        ShopProductDocument.Coupon copy = new ShopProductDocument.Coupon();
        copy.setCouponId(sequenceService.next("product_coupon_rules"));
        copy.setCouponCode(source.getCouponCode());
        copy.setCouponTitle(source.getCouponTitle());
        copy.setDiscountType(source.getDiscountType());
        copy.setDiscountValue(source.getDiscountValue());
        copy.setMinOrderAmount(source.getMinOrderAmount());
        copy.setMaxDiscountAmount(source.getMaxDiscountAmount());
        copy.setStartsAt(source.getStartsAt());
        copy.setEndsAt(source.getEndsAt());
        copy.setActive(false);
        return copy;
    }

    private <T> List<T> safeList(List<T> source) {
        return source == null ? List.of() : source;
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null || source.isEmpty() ? null : new LinkedHashMap<>(source);
    }

    private Map<String, Object> emptyIfNull(Map<String, Object> source) {
        return source == null ? Map.of() : source;
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
        return value == null || value.isBlank() ? null : value.trim();
    }
}
