package com.msa.shop_orders.provider.shop.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "shops")
public class ShopShellView {
    @Id
    private Long shopId;
    private Long ownerUserId;
    private String shopCode;
    private String ownerName;
    private String shopName;
    private Long shopTypeId;
    private Long logoFileId;
    private Long coverFileId;
    private String restaurantServiceType;
    private String approvalStatus;
    private String operationalStatus;
    private Boolean businessSetupComplete;
    private BigDecimal avgRating;
    private Integer totalReviews;
    private RestaurantCoupon restaurantCoupon;

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getShopCode() {
        return shopCode;
    }

    public void setShopCode(String shopCode) {
        this.shopCode = shopCode;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public Long getShopTypeId() {
        return shopTypeId;
    }

    public void setShopTypeId(Long shopTypeId) {
        this.shopTypeId = shopTypeId;
    }

    public Long getLogoFileId() {
        return logoFileId;
    }

    public void setLogoFileId(Long logoFileId) {
        this.logoFileId = logoFileId;
    }

    public Long getCoverFileId() {
        return coverFileId;
    }

    public void setCoverFileId(Long coverFileId) {
        this.coverFileId = coverFileId;
    }

    public String getRestaurantServiceType() {
        return restaurantServiceType;
    }

    public void setRestaurantServiceType(String restaurantServiceType) {
        this.restaurantServiceType = restaurantServiceType;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public Boolean getBusinessSetupComplete() {
        return businessSetupComplete;
    }

    public void setBusinessSetupComplete(Boolean businessSetupComplete) {
        this.businessSetupComplete = businessSetupComplete;
    }

    public boolean isBusinessSetupComplete() {
        return Boolean.TRUE.equals(businessSetupComplete);
    }

    public BigDecimal getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(BigDecimal avgRating) {
        this.avgRating = avgRating;
    }

    public Integer getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Integer totalReviews) {
        this.totalReviews = totalReviews;
    }

    public RestaurantCoupon getRestaurantCoupon() {
        return restaurantCoupon;
    }

    public void setRestaurantCoupon(RestaurantCoupon restaurantCoupon) {
        this.restaurantCoupon = restaurantCoupon;
    }

    public static class RestaurantCoupon {
        private String couponCode;
        private String couponTitle;
        private String discountType;
        private BigDecimal discountValue;
        private BigDecimal minOrderAmount;
        private BigDecimal maxDiscountAmount;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Boolean active;

        public String getCouponCode() {
            return couponCode;
        }

        public void setCouponCode(String couponCode) {
            this.couponCode = couponCode;
        }

        public String getCouponTitle() {
            return couponTitle;
        }

        public void setCouponTitle(String couponTitle) {
            this.couponTitle = couponTitle;
        }

        public String getDiscountType() {
            return discountType;
        }

        public void setDiscountType(String discountType) {
            this.discountType = discountType;
        }

        public BigDecimal getDiscountValue() {
            return discountValue;
        }

        public void setDiscountValue(BigDecimal discountValue) {
            this.discountValue = discountValue;
        }

        public BigDecimal getMinOrderAmount() {
            return minOrderAmount;
        }

        public void setMinOrderAmount(BigDecimal minOrderAmount) {
            this.minOrderAmount = minOrderAmount;
        }

        public BigDecimal getMaxDiscountAmount() {
            return maxDiscountAmount;
        }

        public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) {
            this.maxDiscountAmount = maxDiscountAmount;
        }

        public LocalDateTime getStartsAt() {
            return startsAt;
        }

        public void setStartsAt(LocalDateTime startsAt) {
            this.startsAt = startsAt;
        }

        public LocalDateTime getEndsAt() {
            return endsAt;
        }

        public void setEndsAt(LocalDateTime endsAt) {
            this.endsAt = endsAt;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }
    }
}
