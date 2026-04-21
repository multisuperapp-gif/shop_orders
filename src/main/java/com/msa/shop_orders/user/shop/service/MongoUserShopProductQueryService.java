package com.msa.shop_orders.user.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.ShopCategoryEntity;
import com.msa.shop_orders.persistence.mongo.document.ShopProductDocument;
import com.msa.shop_orders.persistence.mongo.repository.ShopProductMongoQueryRepository;
import com.msa.shop_orders.persistence.mongo.repository.ShopProductMongoRepository;
import com.msa.shop_orders.persistence.repository.ShopCategoryRepository;
import com.msa.shop_orders.provider.shop.dto.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app.mongodb", name = "enabled", havingValue = "true")
public class MongoUserShopProductQueryService implements UserShopProductQueryService {
    private final ShopProductMongoRepository productMongoRepository;
    private final ShopProductMongoQueryRepository productMongoQueryRepository;
    private final ShopCategoryRepository shopCategoryRepository;

    public MongoUserShopProductQueryService(
            ShopProductMongoRepository productMongoRepository,
            ShopProductMongoQueryRepository productMongoQueryRepository,
            ShopCategoryRepository shopCategoryRepository
    ) {
        this.productMongoRepository = productMongoRepository;
        this.productMongoQueryRepository = productMongoQueryRepository;
        this.shopCategoryRepository = shopCategoryRepository;
    }

    @Override
    public List<ShopProductData> products(Long shopId, Long categoryId, String search) {
        List<ShopProductDocument> products = productMongoQueryRepository.searchActive(shopId, categoryId, search);
        return toProductData(products);
    }

    @Override
    public ShopProductData product(Long productId) {
        ShopProductDocument product = productMongoRepository.findByProductIdAndActiveTrue(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found.", HttpStatus.NOT_FOUND));
        return toProductData(List.of(product)).getFirst();
    }

    private List<ShopProductData> toProductData(List<ShopProductDocument> products) {
        Map<Long, ShopCategoryEntity> categories = shopCategoryRepository.findAllById(
                        products.stream().map(ShopProductDocument::getShopCategoryId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ShopCategoryEntity::getId, Function.identity()));
        return products.stream().map(product -> {
            List<ShopProductDocument.Variant> variants = safeList(product.getVariants()).stream()
                    .filter(ShopProductDocument.Variant::isActive)
                    .toList();
            ShopProductDocument.Variant defaultVariant = variants.stream()
                    .filter(ShopProductDocument.Variant::isDefaultVariant)
                    .findFirst()
                    .orElseGet(() -> variants.isEmpty() ? null : variants.getFirst());
            ShopProductDocument.Inventory inventory = defaultVariant == null ? null : defaultVariant.getInventory();
            List<ShopProductDocument.Image> images = safeList(product.getImages()).stream()
                    .sorted(Comparator.comparing(ShopProductDocument.Image::isPrimaryImage).reversed()
                            .thenComparing(ShopProductDocument.Image::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            ShopProductDocument.Image primaryImage = images.stream().filter(ShopProductDocument.Image::isPrimaryImage).findFirst()
                    .orElseGet(() -> images.isEmpty() ? null : images.getFirst());
            ShopCategoryEntity category = categories.get(product.getShopCategoryId());
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
                    product.getAttributes() == null ? Map.of() : product.getAttributes(),
                    product.getAvgRating() == null ? BigDecimal.ZERO.setScale(2) : product.getAvgRating(),
                    zeroIfNull(product.getTotalReviews()),
                    zeroIfNull(product.getTotalOrders()),
                    toPromotionData(product.getPromotion()),
                    toCouponData(product.getCoupon()),
                    null,
                    toVariantData(variants),
                    toImageData(images),
                    product.getUpdatedAt() == null ? LocalDateTime.now() : product.getUpdatedAt()
            );
        }).toList();
    }

    private List<ShopProductVariantData> toVariantData(List<ShopProductDocument.Variant> variants) {
        return variants.stream().map(variant -> {
            ShopProductDocument.Inventory inventory = variant.getInventory();
            Map<String, Object> attributes = variant.getAttributes() == null ? Map.of() : variant.getAttributes();
            return new ShopProductVariantData(
                    variant.getVariantId(),
                    variant.getVariantName(),
                    attributes.get("colorName") == null ? null : String.valueOf(attributes.get("colorName")),
                    attributes.get("colorHex") == null ? null : String.valueOf(attributes.get("colorHex")),
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

    private <T> List<T> safeList(List<T> source) {
        return source == null ? List.of() : source;
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
