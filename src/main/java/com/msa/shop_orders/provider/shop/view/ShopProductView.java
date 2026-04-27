package com.msa.shop_orders.provider.shop.view;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "shop_products")
@CompoundIndex(name = "idx_shop_products_shop_category_updated", def = "{'shopId': 1, 'categoryId': 1, 'updatedAt': -1}")
@Getter
@Setter
public class ShopProductView {
    @Id
    private Long productId;

    @Indexed
    private Long shopId;

    @Indexed
    private Long categoryId;

    private String categoryName;
    private String sku;
    private String itemName;
    private String shortDescription;
    private String description;
    private String brandName;
    private String variantName;
    private BigDecimal unitValue;
    private String unitType;
    private BigDecimal weightInGrams;
    private BigDecimal mrp;
    private BigDecimal sellingPrice;
    private Integer quantityAvailable;
    private Integer reservedQuantity;
    private Integer reorderLevel;
    private String inventoryStatus;
    private Long imageFileId;
    private boolean active;
    private boolean featured;
    private boolean requiresPrescription;
    private String productType;
    private Map<String, Object> attributes;
    private BigDecimal avgRating;
    private Integer totalReviews;
    private Integer totalOrders;
    private Promotion promotion;
    private Coupon coupon;
    private DeliveryRule deliveryRule;
    private List<Variant> variants;
    private List<Image> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    public static class Variant {
        private Long variantId;
        private String variantName;
        private String colorName;
        private String colorHex;
        private BigDecimal unitValue;
        private String unitType;
        private BigDecimal weightInGrams;
        private BigDecimal mrp;
        private BigDecimal sellingPrice;
        private Integer quantityAvailable;
        private Integer reservedQuantity;
        private Integer reorderLevel;
        private String inventoryStatus;
        private boolean defaultVariant;
        private boolean active;
        private Integer sortOrder;
        private Map<String, Object> attributes;
    }

    @Getter
    @Setter
    public static class Image {
        private Long imageId;
        private Long fileId;
        private String imageRole;
        private Long variantId;
        private Integer sortOrder;
        private boolean primaryImage;
    }

    @Getter
    @Setter
    public static class Promotion {
        private Long promotionId;
        private String promotionType;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Integer priorityScore;
        private BigDecimal paidAmount;
        private String status;
    }

    @Getter
    @Setter
    public static class Coupon {
        private Long couponId;
        private String couponCode;
        private String couponTitle;
        private String discountType;
        private BigDecimal discountValue;
        private BigDecimal minOrderAmount;
        private BigDecimal maxDiscountAmount;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private boolean active;
    }

    @Getter
    @Setter
    public static class DeliveryRule {
        private Long shopLocationId;
        private String deliveryType;
        private BigDecimal radiusKm;
        private BigDecimal minOrderAmount;
        private BigDecimal deliveryFee;
        private BigDecimal freeDeliveryAbove;
        private Integer orderCutoffMinutesBeforeClose;
        private Integer closingSoonMinutes;
    }
}
