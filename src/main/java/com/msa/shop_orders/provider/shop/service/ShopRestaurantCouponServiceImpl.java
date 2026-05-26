package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.common.shoptype.ShopTypeFamilyResolver;
import com.msa.shop_orders.provider.shop.dto.ShopRestaurantCouponData;
import com.msa.shop_orders.provider.shop.dto.ShopRestaurantCouponUpdateRequest;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ShopRestaurantCouponServiceImpl implements ShopRestaurantCouponService {
    private final ShopContextService shopContextService;
    private final ShopShellViewRepository shopShellViewRepository;
    private final ShopTypeFamilyResolver shopTypeFamilyResolver;

    public ShopRestaurantCouponServiceImpl(
            ShopContextService shopContextService,
            ShopShellViewRepository shopShellViewRepository,
            ShopTypeFamilyResolver shopTypeFamilyResolver
    ) {
        this.shopContextService = shopContextService;
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopTypeFamilyResolver = shopTypeFamilyResolver;
    }

    @Override
    public ShopRestaurantCouponData fetchCurrent() {
        ShopShellView shop = requireRestaurantShop();
        return toData(shop.getRestaurantCoupon());
    }

    @Override
    @Transactional
    public ShopRestaurantCouponData saveCurrent(ShopRestaurantCouponUpdateRequest request) {
        ShopShellView shop = requireRestaurantShop();
        ShopShellView.RestaurantCoupon coupon = buildCoupon(request);
        shop.setRestaurantCoupon(coupon);
        shopShellViewRepository.save(shop);
        return toData(coupon);
    }

    private ShopShellView requireRestaurantShop() {
        ShopShellView shop = shopContextService.currentApprovedShop();
        if (shopTypeFamilyResolver.resolveFamily(shop) != ShopTypeFamily.RESTAURANT) {
            throw new BusinessException(
                    "RESTAURANT_ONLY_SETTING",
                    "Restaurant coupon settings are available only for restaurant shops.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return shop;
    }

    private ShopShellView.RestaurantCoupon buildCoupon(ShopRestaurantCouponUpdateRequest request) {
        if (request == null) {
            return null;
        }
        boolean active = Boolean.TRUE.equals(request.active());
        boolean hasPayload = active
                || StringUtils.hasText(request.couponCode())
                || StringUtils.hasText(request.discountType())
                || request.discountValue() != null
                || request.startsAt() != null
                || request.endsAt() != null;
        if (!hasPayload) {
            return null;
        }

        String couponCode = normalizeRequiredText(request.couponCode(), "COUPON_CODE_REQUIRED", "Coupon code is required.");
        String discountType = normalizeDiscountType(request.discountType());
        BigDecimal discountValue = requirePositiveAmount(
                request.discountValue(),
                "COUPON_DISCOUNT_REQUIRED",
                "Coupon discount value is required."
        );
        if ("PERCENTAGE".equals(discountType) && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(
                    "COUPON_PERCENTAGE_INVALID",
                    "Percentage discount cannot be more than 100.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (request.startsAt() == null || request.endsAt() == null) {
            throw new BusinessException(
                    "COUPON_DATES_REQUIRED",
                    "Coupon start and end date are required.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (request.endsAt().isBefore(request.startsAt())) {
            throw new BusinessException(
                    "COUPON_DATE_RANGE_INVALID",
                    "Coupon end date must be after the start date.",
                    HttpStatus.BAD_REQUEST
            );
        }

        ShopShellView.RestaurantCoupon coupon = new ShopShellView.RestaurantCoupon();
        coupon.setCouponCode(couponCode);
        coupon.setCouponTitle(blankToNull(request.couponTitle()));
        coupon.setDiscountType(discountType);
        coupon.setDiscountValue(discountValue);
        coupon.setMinOrderAmount(request.minOrderAmount());
        coupon.setMaxDiscountAmount(request.maxDiscountAmount());
        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        coupon.setActive(active);
        return coupon;
    }

    private ShopRestaurantCouponData toData(ShopShellView.RestaurantCoupon coupon) {
        if (coupon == null) {
            return new ShopRestaurantCouponData(null, null, null, null, null, null, null, null, false);
        }
        return new ShopRestaurantCouponData(
                coupon.getCouponCode(),
                coupon.getCouponTitle(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinOrderAmount(),
                coupon.getMaxDiscountAmount(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                Boolean.TRUE.equals(coupon.getActive())
        );
    }

    private String normalizeRequiredText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private String normalizeDiscountType(String value) {
        if (!StringUtils.hasText(value)) {
            return "PERCENTAGE";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"PERCENTAGE".equals(normalized) && !"FLAT".equals(normalized)) {
            throw new BusinessException(
                    "COUPON_DISCOUNT_TYPE_INVALID",
                    "Coupon discount type must be PERCENTAGE or FLAT.",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private BigDecimal requirePositiveAmount(BigDecimal value, String code, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
