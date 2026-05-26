package com.msa.shop_orders.provider.shop.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursData;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursUpdateRequest;
import com.msa.shop_orders.provider.shop.dto.ShopRestaurantCouponData;
import com.msa.shop_orders.provider.shop.dto.ShopRestaurantCouponUpdateRequest;
import com.msa.shop_orders.provider.shop.service.ShopOperatingHoursService;
import com.msa.shop_orders.provider.shop.service.ShopRestaurantCouponService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shops/settings")
public class ShopSettingsController {
    private final ShopOperatingHoursService shopOperatingHoursService;
    private final ShopRestaurantCouponService shopRestaurantCouponService;

    public ShopSettingsController(
            ShopOperatingHoursService shopOperatingHoursService,
            ShopRestaurantCouponService shopRestaurantCouponService
    ) {
        this.shopOperatingHoursService = shopOperatingHoursService;
        this.shopRestaurantCouponService = shopRestaurantCouponService;
    }

    @GetMapping("/operating-hours")
    public ApiResponse<ShopOperatingHoursData> fetchOperatingHours() {
        return ApiResponse.success(null, shopOperatingHoursService.fetchCurrent());
    }

    @PutMapping("/operating-hours")
    public ApiResponse<ShopOperatingHoursData> saveOperatingHours(
            @Valid @RequestBody ShopOperatingHoursUpdateRequest request
    ) {
        return ApiResponse.success(
                "Operating hours updated successfully.",
                shopOperatingHoursService.saveCurrent(request)
        );
    }

    @GetMapping("/restaurant-coupon")
    public ApiResponse<ShopRestaurantCouponData> fetchRestaurantCoupon() {
        return ApiResponse.success(null, shopRestaurantCouponService.fetchCurrent());
    }

    @PutMapping("/restaurant-coupon")
    public ApiResponse<ShopRestaurantCouponData> saveRestaurantCoupon(
            @Valid @RequestBody ShopRestaurantCouponUpdateRequest request
    ) {
        return ApiResponse.success(
                "Restaurant coupon updated successfully.",
                shopRestaurantCouponService.saveCurrent(request)
        );
    }
}
