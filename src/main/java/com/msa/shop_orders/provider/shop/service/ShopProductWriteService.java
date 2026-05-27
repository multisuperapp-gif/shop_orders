package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.mongo.MongoSequenceService;
import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductCouponRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductImageRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductPromotionRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductVariantRequest;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.ShopProductView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ShopProductWriteService {
    private static final Pattern COLOR_HEX_PATTERN = Pattern.compile("^#?[0-9A-Fa-f]{6}$");

    private final ShopProductViewRepository shopProductViewRepository;
    private final ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    private final MongoSequenceService mongoSequenceService;

    public ShopProductWriteService(
            ShopProductViewRepository shopProductViewRepository,
            ShopDeliveryRuleViewService shopDeliveryRuleViewService,
            MongoSequenceService mongoSequenceService
    ) {
        this.shopProductViewRepository = shopProductViewRepository;
        this.shopDeliveryRuleViewService = shopDeliveryRuleViewService;
        this.mongoSequenceService = mongoSequenceService;
    }

    @Transactional
    public ProductEntity createProduct(ShopShellView shop, ShopCategoryView category, ShopCreateProductRequest request) {
        return createProductDocument(shop, category, request);
    }

    @Transactional
    public ProductEntity updateProduct(ShopShellView shop, Long productId, ShopCategoryView category, ShopCreateProductRequest request) {
        return updateProductDocument(shop, productId, category, request);
    }

    @Transactional
    public ProductEntity duplicateProduct(ShopShellView shop, Long productId) {
        return duplicateProductDocument(shop, productId);
    }

    @Transactional
    public ProductEntity updateProductStatus(Long productId, boolean active) {
        return updateProductStatusDocument(productId, active);
    }

    @Transactional
    public ProductEntity updateProductFeatured(Long productId, boolean featured) {
        return updateProductFeaturedDocument(productId, featured);
    }

    @Transactional
    public ProductEntity updatePromotionStatus(Long productId, String status) {
        return updatePromotionStatusDocument(productId, status);
    }

    @Transactional
    public ProductEntity updateCouponStatus(Long productId, boolean active) {
        return updateCouponStatusDocument(productId, active);
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

    private ProductEntity createProductDocument(ShopShellView shop, ShopCategoryView category, ShopCreateProductRequest request) {
        Long productId = mongoSequenceService.nextValue("shop-product-id");
        String sku = resolveSku(request.sku(), shop.getShopId(), category.getCategoryId(), request.itemName());
        LocalDateTime now = LocalDateTime.now();
        ShopProductView document = new ShopProductView();
        document.setProductId(productId);
        document.setShopId(shop.getShopId());
        document.setCategoryId(category.getCategoryId());
        document.setCategoryName(category.getName());
        document.setSku(sku);
        document.setProductCode(sku);
        document.setAvgRating(BigDecimal.ZERO.setScale(2));
        document.setTotalReviews(0);
        document.setTotalOrders(0);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        document.setDeliveryRule(toDeliveryRuleDocument(shop.getShopId()));
        applyRequestToProductDocument(document, request, false);
        shopProductViewRepository.save(document);
        return toProductEntity(document);
    }

    private ProductEntity updateProductDocument(ShopShellView shop, Long productId, ShopCategoryView category, ShopCreateProductRequest request) {
        ShopProductView document = requireOwnedProductDocument(productId, shop.getShopId());
        document.setCategoryId(category.getCategoryId());
        document.setCategoryName(category.getName());
        if (request.sku() != null && !request.sku().isBlank()) {
            String normalizedSku = request.sku().trim();
            if (!normalizedSku.equalsIgnoreCase(document.getSku()) && skuExists(normalizedSku)) {
                throw new BusinessException("DUPLICATE_SKU", "SKU already exists.", HttpStatus.BAD_REQUEST);
            }
            document.setSku(normalizedSku);
        }
        if (document.getSku() != null && !document.getSku().isBlank()) {
            document.setProductCode(document.getSku());
        }
        document.setUpdatedAt(LocalDateTime.now());
        document.setDeliveryRule(toDeliveryRuleDocument(shop.getShopId()));
        applyRequestToProductDocument(document, request, true);
        shopProductViewRepository.save(document);
        return toProductEntity(document);
    }

    private ProductEntity duplicateProductDocument(ShopShellView shop, Long productId) {
        ShopProductView source = requireOwnedProductDocument(productId, shop.getShopId());
        ShopProductView duplicate = new ShopProductView();
        duplicate.setProductId(mongoSequenceService.nextValue("shop-product-id"));
        duplicate.setShopId(source.getShopId());
        duplicate.setCategoryId(source.getCategoryId());
        duplicate.setCategoryName(source.getCategoryName());
        duplicate.setSku(resolveDuplicateSku(source.getSku()));
        duplicate.setProductCode(duplicate.getSku());
        duplicate.setItemName(resolveDuplicateItemName(source.getItemName()));
        duplicate.setShortDescription(source.getShortDescription());
        duplicate.setDescription(source.getDescription());
        duplicate.setBrandName(source.getBrandName());
        duplicate.setProductType(source.getProductType());
        duplicate.setRequiresPrescription(source.isRequiresPrescription());
        duplicate.setAttributes(source.getAttributes());
        duplicate.setAvgRating(BigDecimal.ZERO.setScale(2));
        duplicate.setTotalReviews(0);
        duplicate.setTotalOrders(0);
        duplicate.setActive(false);
        duplicate.setFeatured(false);
        duplicate.setPromotion(copyPromotionDocument(source.getPromotion(), duplicate.getProductId(), shop.getShopId()));
        duplicate.setCoupon(copyCouponDocument(source.getCoupon(), duplicate.getProductId(), shop.getShopId()));
        duplicate.setDeliveryRule(toDeliveryRuleDocument(shop.getShopId()));
        duplicate.setVariants(copyVariantDocuments(source.getVariants()));
        duplicate.setImages(copyImageDocuments(source.getImages(), duplicate.getVariants()));
        updatePrimaryVariantFields(duplicate);
        updatePrimaryImageField(duplicate);
        duplicate.setCreatedAt(LocalDateTime.now());
        duplicate.setUpdatedAt(LocalDateTime.now());
        shopProductViewRepository.save(duplicate);
        return toProductEntity(duplicate);
    }

    private ProductEntity updateProductStatusDocument(Long productId, boolean active) {
        ShopProductView document = requireProductDocument(productId);
        document.setActive(active);
        if (!active) {
            document.setFeatured(false);
        }
        if (document.getVariants() != null) {
            document.getVariants().forEach(variant -> variant.setInventoryStatus(resolveInventoryStatus(
                    variant.getQuantityAvailable(),
                    variant.getReorderLevel(),
                    active && variant.isActive()
            )));
        }
        document.setInventoryStatus(resolvePrimaryInventoryStatus(document));
        document.setUpdatedAt(LocalDateTime.now());
        shopProductViewRepository.save(document);
        return toProductEntity(document);
    }

    private ProductEntity updateProductFeaturedDocument(Long productId, boolean featured) {
        ShopProductView document = requireProductDocument(productId);
        document.setFeatured(featured);
        document.setUpdatedAt(LocalDateTime.now());
        shopProductViewRepository.save(document);
        return toProductEntity(document);
    }

    private ProductEntity updatePromotionStatusDocument(Long productId, String status) {
        ShopProductView document = requireProductDocument(productId);
        if (document.getPromotion() == null) {
            throw new BusinessException("PROMOTION_NOT_FOUND", "Promotion not found.", HttpStatus.NOT_FOUND);
        }
        document.getPromotion().setStatus(status == null ? null : status.trim().toUpperCase(Locale.ROOT));
        document.setUpdatedAt(LocalDateTime.now());
        shopProductViewRepository.save(document);
        return toProductEntity(document);
    }

    private ProductEntity updateCouponStatusDocument(Long productId, boolean active) {
        ShopProductView document = requireProductDocument(productId);
        if (document.getCoupon() == null) {
            throw new BusinessException("COUPON_NOT_FOUND", "Coupon not found.", HttpStatus.NOT_FOUND);
        }
        document.getCoupon().setActive(active);
        document.setUpdatedAt(LocalDateTime.now());
        shopProductViewRepository.save(document);
        return toProductEntity(document);
    }

    private void applyRequestToProductDocument(ShopProductView document, ShopCreateProductRequest request, boolean updateMode) {
        if (document.getSku() != null && !document.getSku().isBlank()) {
            document.setProductCode(document.getSku());
        }
        document.setItemName(normalizeDisplayText(request.itemName()));
        document.setShortDescription(blankToNull(request.shortDescription()));
        document.setDescription(blankToNull(request.description()));
        document.setBrandName(blankToNull(request.brandName()));
        document.setProductType(resolveProductType(request.productType()));
        document.setRequiresPrescription(Boolean.TRUE.equals(request.requiresPrescription()));
        document.setAttributes(request.attributes() == null ? null : new LinkedHashMap<>(request.attributes()));
        document.setActive(request.active() == null || request.active());
        document.setFeatured(Boolean.TRUE.equals(request.featured()));
        document.setPromotion(buildPromotionDocument(document.getProductId(), document.getShopId(), request.promotion()));
        document.setCoupon(buildCouponDocument(document.getProductId(), document.getShopId(), request.coupon()));
        document.setVariants(buildVariantDocuments(document, request, updateMode));
        document.setImages(buildImageDocuments(document.getProductId(), document.getImages(), request, document.getVariants(), updateMode));
        updatePrimaryVariantFields(document);
        updatePrimaryImageField(document);
        document.setInventoryStatus(resolvePrimaryInventoryStatus(document));
    }

    private List<ShopProductView.Variant> buildVariantDocuments(ShopProductView document, ShopCreateProductRequest request, boolean updateMode) {
        List<ShopProductVariantRequest> requestedVariants = normalizeVariantRequests(request);
        if (requestedVariants.isEmpty()) {
            throw new BusinessException("PRODUCT_VARIANT_REQUIRED", "At least one product variant is required.", HttpStatus.BAD_REQUEST);
        }
        List<ShopProductView.Variant> existingVariants = Optional.ofNullable(document.getVariants()).orElse(List.of());
        Map<Long, ShopProductView.Variant> existingById = existingVariants.stream()
                .filter(variant -> variant.getVariantId() != null)
                .collect(Collectors.toMap(ShopProductView.Variant::getVariantId, Function.identity()));
        int defaultIndex = resolveDefaultVariantIndex(requestedVariants);
        List<ShopProductView.Variant> variants = new ArrayList<>();
        for (int index = 0; index < requestedVariants.size(); index++) {
            ShopProductVariantRequest requestedVariant = requestedVariants.get(index);
            validateVariantRequest(requestedVariant);
            ShopProductView.Variant variant = requestedVariant.variantId() == null
                    ? null
                    : existingById.get(requestedVariant.variantId());
            if (variant == null) {
                variant = new ShopProductView.Variant();
                variant.setVariantId(mongoSequenceService.nextValue("shop-product-variant-id"));
            }
            variant.setVariantName(resolveVariantName(document.getItemName(), requestedVariant, index));
            variant.setAttributes(mergeVariantAttributes(requestedVariant));
            variant.setColorName(requestedVariant.colorName());
            variant.setColorHex(requestedVariant.colorHex());
            variant.setUnitValue(requestedVariant.unitValue());
            variant.setUnitType(blankToNull(requestedVariant.unitType()));
            variant.setWeightInGrams(requestedVariant.weightInGrams());
            variant.setMrp(requestedVariant.mrp());
            variant.setSellingPrice(requestedVariant.sellingPrice());
            variant.setQuantityAvailable(zeroIfNull(requestedVariant.quantityAvailable()));
            variant.setReservedQuantity(resolveReservedQuantity(existingById.get(variant.getVariantId())));
            variant.setReorderLevel(requestedVariant.reorderLevel());
            variant.setDefaultVariant(index == defaultIndex);
            variant.setSortOrder(requestedVariant.sortOrder() == null ? index : requestedVariant.sortOrder());
            variant.setActive(requestedVariant.active() == null || requestedVariant.active());
            variant.setInventoryStatus(resolveInventoryStatus(
                    requestedVariant.quantityAvailable(),
                    requestedVariant.reorderLevel(),
                    document.isActive() && variant.isActive()
            ));
            variants.add(variant);
        }
        if (updateMode && variants.isEmpty()) {
            throw new BusinessException("PRODUCT_VARIANT_REQUIRED", "At least one product variant is required.", HttpStatus.BAD_REQUEST);
        }
        return variants;
    }

    private List<ShopProductView.Image> buildImageDocuments(
            Long productId,
            List<ShopProductView.Image> existingImages,
            ShopCreateProductRequest request,
            List<ShopProductView.Variant> variants,
            boolean updateMode
    ) {
        List<ShopProductImageRequest> requestedImages = request.images();
        Map<String, Long> variantIdsByClientKey = buildVariantIdMap(request, variants);
        if (requestedImages != null) {
            List<ShopProductImageRequest> normalizedImages = requestedImages.stream().filter(Objects::nonNull).toList();
            boolean hasPrimary = normalizedImages.stream().anyMatch(image -> Boolean.TRUE.equals(image.primaryImage()));
            List<ShopProductView.Image> images = new ArrayList<>();
            for (int index = 0; index < normalizedImages.size(); index++) {
                ShopProductImageRequest imageRequest = normalizedImages.get(index);
                ShopProductView.Image image = new ShopProductView.Image();
                image.setImageId(mongoSequenceService.nextValue("shop-product-image-id"));
                image.setFileId(imageRequest.fileId());
                image.setVariantId(resolveVariantId(imageRequest.variantClientKey(), variantIdsByClientKey));
                image.setImageRole(resolveImageRole(imageRequest.imageRole(), imageRequest.primaryImage()));
                image.setSortOrder(imageRequest.sortOrder() == null ? index : imageRequest.sortOrder());
                image.setPrimaryImage(Boolean.TRUE.equals(imageRequest.primaryImage()) || (!hasPrimary && index == 0));
                images.add(image);
            }
            return images;
        }
        if (request.imageFileId() != null) {
            ShopProductView.Image image = new ShopProductView.Image();
            image.setImageId(mongoSequenceService.nextValue("shop-product-image-id"));
            image.setFileId(request.imageFileId());
            image.setVariantId(null);
            image.setImageRole("COVER");
            image.setSortOrder(0);
            image.setPrimaryImage(true);
            return List.of(image);
        }
        return updateMode ? Optional.ofNullable(existingImages).orElse(List.of()) : List.of();
    }

    private Map<String, Long> buildVariantIdMap(ShopCreateProductRequest request, List<ShopProductView.Variant> variants) {
        List<ShopProductVariantRequest> requestedVariants = normalizeVariantRequests(request);
        Map<String, Long> variantIdsByClientKey = new LinkedHashMap<>();
        for (int index = 0; index < requestedVariants.size() && index < variants.size(); index++) {
            variantIdsByClientKey.put(resolveVariantClientKey(requestedVariants.get(index), index), variants.get(index).getVariantId());
        }
        return variantIdsByClientKey;
    }

    private ShopProductView.Promotion buildPromotionDocument(Long productId, Long shopId, ShopProductPromotionRequest promotionRequest) {
        if (promotionRequest == null || !hasPromotionPayload(promotionRequest)) {
            return null;
        }
        ShopProductView.Promotion promotion = new ShopProductView.Promotion();
        promotion.setPromotionId(mongoSequenceService.nextValue("shop-product-promotion-id"));
        promotion.setPromotionType(normalizePromotionType(promotionRequest.promotionType()));
        promotion.setStartsAt(requirePromotionDate(promotionRequest.startsAt(), "PROMOTION_START_REQUIRED"));
        promotion.setEndsAt(requirePromotionDate(promotionRequest.endsAt(), "PROMOTION_END_REQUIRED"));
        if (promotion.getEndsAt().isBefore(promotion.getStartsAt())) {
            throw new BusinessException("PROMOTION_DATE_RANGE_INVALID", "Promotion end date must be after start date.", HttpStatus.BAD_REQUEST);
        }
        if (promotionRequest.priorityScore() != null && promotionRequest.priorityScore() < 0) {
            throw new BusinessException("PROMOTION_PRIORITY_INVALID", "Promotion priority score cannot be negative.", HttpStatus.BAD_REQUEST);
        }
        promotion.setPriorityScore(promotionRequest.priorityScore() == null ? 0 : promotionRequest.priorityScore());
        promotion.setPaidAmount(promotionRequest.paidAmount() == null ? BigDecimal.ZERO : promotionRequest.paidAmount());
        promotion.setStatus(normalizePromotionStatus(promotionRequest.status()));
        return promotion;
    }

    private ShopProductView.Coupon buildCouponDocument(Long productId, Long shopId, ShopProductCouponRequest couponRequest) {
        if (couponRequest == null || !hasCouponPayload(couponRequest)) {
            return null;
        }
        ShopProductView.Coupon coupon = new ShopProductView.Coupon();
        coupon.setCouponId(mongoSequenceService.nextValue("shop-product-coupon-id"));
        coupon.setCouponCode(normalizeRequiredText(couponRequest.couponCode(), "COUPON_CODE_REQUIRED", "Coupon code is required."));
        coupon.setCouponTitle(blankToNull(couponRequest.couponTitle()));
        coupon.setDiscountType(normalizeCouponDiscountType(couponRequest.discountType()));
        coupon.setDiscountValue(requireAmount(couponRequest.discountValue(), "COUPON_DISCOUNT_REQUIRED", "Coupon discount value is required."));
        coupon.setMinOrderAmount(couponRequest.minOrderAmount());
        coupon.setMaxDiscountAmount(couponRequest.maxDiscountAmount());
        coupon.setStartsAt(requireCouponDate(couponRequest.startsAt(), "COUPON_START_REQUIRED"));
        coupon.setEndsAt(requireCouponDate(couponRequest.endsAt(), "COUPON_END_REQUIRED"));
        if (coupon.getEndsAt().isBefore(coupon.getStartsAt())) {
            throw new BusinessException("COUPON_DATE_RANGE_INVALID", "Coupon end date must be after start date.", HttpStatus.BAD_REQUEST);
        }
        if ("PERCENTAGE".equalsIgnoreCase(coupon.getDiscountType())
                && coupon.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("COUPON_PERCENTAGE_INVALID", "Percentage discount cannot be more than 100.", HttpStatus.BAD_REQUEST);
        }
        coupon.setActive(couponRequest.enabled() == null || couponRequest.enabled());
        return coupon;
    }

    private ShopProductView.Promotion copyPromotionDocument(ShopProductView.Promotion source, Long productId, Long shopId) {
        if (source == null) {
            return null;
        }
        ShopProductView.Promotion promotion = new ShopProductView.Promotion();
        promotion.setPromotionId(mongoSequenceService.nextValue("shop-product-promotion-id"));
        promotion.setPromotionType(source.getPromotionType());
        promotion.setStartsAt(source.getStartsAt());
        promotion.setEndsAt(source.getEndsAt());
        promotion.setPriorityScore(source.getPriorityScore());
        promotion.setPaidAmount(source.getPaidAmount());
        promotion.setStatus("DRAFT");
        return promotion;
    }

    private ShopProductView.Coupon copyCouponDocument(ShopProductView.Coupon source, Long productId, Long shopId) {
        if (source == null) {
            return null;
        }
        ShopProductView.Coupon coupon = new ShopProductView.Coupon();
        coupon.setCouponId(mongoSequenceService.nextValue("shop-product-coupon-id"));
        coupon.setCouponCode(source.getCouponCode());
        coupon.setCouponTitle(source.getCouponTitle());
        coupon.setDiscountType(source.getDiscountType());
        coupon.setDiscountValue(source.getDiscountValue());
        coupon.setMinOrderAmount(source.getMinOrderAmount());
        coupon.setMaxDiscountAmount(source.getMaxDiscountAmount());
        coupon.setStartsAt(source.getStartsAt());
        coupon.setEndsAt(source.getEndsAt());
        coupon.setActive(false);
        return coupon;
    }

    private List<ShopProductView.Variant> copyVariantDocuments(List<ShopProductView.Variant> sourceVariants) {
        if (sourceVariants == null || sourceVariants.isEmpty()) {
            return List.of();
        }
        List<ShopProductView.Variant> duplicates = new ArrayList<>();
        for (ShopProductView.Variant source : sourceVariants) {
            ShopProductView.Variant variant = new ShopProductView.Variant();
            variant.setVariantId(mongoSequenceService.nextValue("shop-product-variant-id"));
            variant.setVariantName(source.getVariantName());
            variant.setColorName(source.getColorName());
            variant.setColorHex(source.getColorHex());
            variant.setUnitValue(source.getUnitValue());
            variant.setUnitType(source.getUnitType());
            variant.setWeightInGrams(source.getWeightInGrams());
            variant.setMrp(source.getMrp());
            variant.setSellingPrice(source.getSellingPrice());
            variant.setQuantityAvailable(zeroIfNull(source.getQuantityAvailable()));
            variant.setReservedQuantity(0);
            variant.setReorderLevel(source.getReorderLevel());
            variant.setDefaultVariant(source.isDefaultVariant());
            variant.setActive(source.isActive());
            variant.setSortOrder(source.getSortOrder());
            variant.setAttributes(source.getAttributes());
            variant.setInventoryStatus(resolveInventoryStatus(variant.getQuantityAvailable(), variant.getReorderLevel(), variant.isActive()));
            duplicates.add(variant);
        }
        return duplicates;
    }

    private List<ShopProductView.Image> copyImageDocuments(List<ShopProductView.Image> sourceImages, List<ShopProductView.Variant> duplicateVariants) {
        if (sourceImages == null || sourceImages.isEmpty()) {
            return List.of();
        }
        Map<Integer, Long> variantIdBySortOrder = duplicateVariants.stream()
                .collect(Collectors.toMap(
                        variant -> variant.getSortOrder() == null ? 0 : variant.getSortOrder(),
                        ShopProductView.Variant::getVariantId,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<ShopProductView.Image> duplicates = new ArrayList<>();
        for (ShopProductView.Image source : sourceImages) {
            ShopProductView.Image image = new ShopProductView.Image();
            image.setImageId(mongoSequenceService.nextValue("shop-product-image-id"));
            image.setFileId(source.getFileId());
            image.setImageRole(source.getImageRole());
            image.setSortOrder(source.getSortOrder());
            image.setPrimaryImage(source.isPrimaryImage());
            image.setVariantId(source.getVariantId() == null ? null : variantIdBySortOrder.get(source.getSortOrder() == null ? 0 : source.getSortOrder()));
            duplicates.add(image);
        }
        return duplicates;
    }

    private ShopProductView.DeliveryRule toDeliveryRuleDocument(Long shopId) {
        ShopProductView.DeliveryRule deliveryRule = new ShopProductView.DeliveryRule();
        shopDeliveryRuleViewService.findPrimaryDeliveryRule(shopId).ifPresent(rule -> {
            deliveryRule.setShopLocationId(rule.shopLocationId());
            deliveryRule.setDeliveryType(rule.deliveryType());
            deliveryRule.setRadiusKm(rule.radiusKm());
            deliveryRule.setMinOrderAmount(rule.minOrderAmount());
            deliveryRule.setDeliveryFee(rule.deliveryFee());
            deliveryRule.setFreeDeliveryAbove(rule.freeDeliveryAbove());
            deliveryRule.setOrderCutoffMinutesBeforeClose(rule.orderCutoffMinutesBeforeClose());
            deliveryRule.setClosingSoonMinutes(rule.closingSoonMinutes());
        });
        return deliveryRule.getDeliveryType() == null ? null : deliveryRule;
    }

    private void updatePrimaryVariantFields(ShopProductView document) {
        ShopProductView.Variant primary = Optional.ofNullable(document.getVariants()).orElse(List.of()).stream()
                .filter(ShopProductView.Variant::isDefaultVariant)
                .findFirst()
                .orElseGet(() -> Optional.ofNullable(document.getVariants()).orElse(List.of()).stream().findFirst().orElse(null));
        if (primary == null) {
            document.setVariantName(null);
            document.setUnitValue(null);
            document.setUnitType(null);
            document.setWeightInGrams(null);
            document.setMrp(null);
            document.setSellingPrice(null);
            document.setQuantityAvailable(0);
            document.setReservedQuantity(0);
            document.setReorderLevel(null);
            document.setInventoryStatus("OUT_OF_STOCK");
            return;
        }
        document.setVariantName(primary.getVariantName());
        document.setUnitValue(primary.getUnitValue());
        document.setUnitType(primary.getUnitType());
        document.setWeightInGrams(primary.getWeightInGrams());
        document.setMrp(primary.getMrp());
        document.setSellingPrice(primary.getSellingPrice());
        document.setQuantityAvailable(primary.getQuantityAvailable());
        document.setReservedQuantity(primary.getReservedQuantity());
        document.setReorderLevel(primary.getReorderLevel());
        document.setInventoryStatus(primary.getInventoryStatus());
    }

    private void updatePrimaryImageField(ShopProductView document) {
        ShopProductView.Image primaryImage = Optional.ofNullable(document.getImages()).orElse(List.of()).stream()
                .filter(ShopProductView.Image::isPrimaryImage)
                .findFirst()
                .orElseGet(() -> Optional.ofNullable(document.getImages()).orElse(List.of()).stream().findFirst().orElse(null));
        document.setImageFileId(primaryImage == null ? null : primaryImage.getFileId());
    }

    private Integer resolveReservedQuantity(ShopProductView.Variant existingVariant) {
        return existingVariant == null || existingVariant.getReservedQuantity() == null ? 0 : existingVariant.getReservedQuantity();
    }

    private String resolvePrimaryInventoryStatus(ShopProductView document) {
        ShopProductView.Variant primary = Optional.ofNullable(document.getVariants()).orElse(List.of()).stream()
                .filter(ShopProductView.Variant::isDefaultVariant)
                .findFirst()
                .orElseGet(() -> Optional.ofNullable(document.getVariants()).orElse(List.of()).stream().findFirst().orElse(null));
        return primary == null ? "OUT_OF_STOCK" : primary.getInventoryStatus();
    }

    private ShopProductView requireProductDocument(Long productId) {
        return shopProductViewRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found.", HttpStatus.NOT_FOUND));
    }

    private ShopProductView requireOwnedProductDocument(Long productId, Long shopId) {
        ShopProductView document = requireProductDocument(productId);
        if (!Objects.equals(document.getShopId(), shopId)) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND);
        }
        return document;
    }

    private ProductEntity toProductEntity(ShopProductView document) {
        ProductEntity entity = new ProductEntity();
        entity.setId(document.getProductId());
        entity.setShopId(document.getShopId());
        entity.setShopCategoryId(document.getCategoryId());
        entity.setSku(document.getSku());
        entity.setName(document.getItemName());
        entity.setShortDescription(document.getShortDescription());
        entity.setDescription(document.getDescription());
        entity.setProductType(document.getProductType());
        entity.setBrandName(document.getBrandName());
        entity.setRequiresPrescription(document.isRequiresPrescription());
        entity.setActive(document.isActive());
        entity.setFeatured(document.isFeatured());
        entity.setAvgRating(document.getAvgRating());
        entity.setTotalReviews(document.getTotalReviews());
        entity.setTotalOrders(document.getTotalOrders());
        return entity;
    }

    private boolean skuExists(String sku) {
        return shopProductViewRepository.existsBySkuIgnoreCase(sku);
    }

    private String resolveSku(String requestedSku, Long shopId, Long categoryId, String itemName) {
        if (requestedSku != null && !requestedSku.isBlank()) {
            String normalizedSku = requestedSku.trim();
            if (skuExists(normalizedSku)) {
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
        while (skuExists(candidate)) {
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

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
