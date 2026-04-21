package com.msa.shop_orders.persistence.mongo.document;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Document(collection = "products")
@CompoundIndexes({
        @CompoundIndex(name = "idx_products_shop_category_active", def = "{'shopId': 1, 'shopCategoryId': 1, 'active': 1}"),
        @CompoundIndex(name = "idx_products_user_listing", def = "{'shopCategoryId': 1, 'active': 1, 'featured': -1, 'avgRating': -1, 'totalOrders': -1}"),
        @CompoundIndex(name = "idx_products_shop_updated", def = "{'shopId': 1, 'updatedAt': -1}")
})
public class ShopProductDocument {
    @Id
    private String id;

    @Indexed(unique = true)
    private Long productId;

    private Long shopId;
    private Long shopCategoryId;

    @Indexed(unique = true, sparse = true)
    private String sku;

    private String name;
    private String description;
    private String shortDescription;
    private String productType;
    private String brandName;
    private Map<String, Object> attributes;
    private boolean requiresPrescription;
    private BigDecimal avgRating = BigDecimal.ZERO;
    private Integer totalReviews = 0;
    private Integer totalOrders = 0;
    private boolean featured;
    private boolean active = true;
    private List<Variant> variants = new ArrayList<>();
    private List<Image> images = new ArrayList<>();
    private Promotion promotion;
    private Coupon coupon;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @Setter
    public static class Variant {
        private Long variantId;
        private String variantName;
        private Map<String, Object> attributes;
        private BigDecimal unitValue;
        private String unitType;
        private BigDecimal weightInGrams;
        private BigDecimal mrp;
        private BigDecimal sellingPrice;
        private boolean defaultVariant;
        private Integer sortOrder;
        private boolean active = true;
        private Inventory inventory;
    }

    @Getter
    @Setter
    public static class Inventory {
        private Integer quantityAvailable = 0;
        private Integer reservedQuantity = 0;
        private Integer reorderLevel;
        private String inventoryStatus = "OUT_OF_STOCK";
    }

    @Getter
    @Setter
    public static class Image {
        private Long imageId;
        private Long variantId;
        private Long fileId;
        private String imageRole;
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
}
