package com.msa.shop_orders.provider.shop.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursData;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursUpdateRequest;
import com.msa.shop_orders.provider.shop.service.ShopOperatingHoursService;
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

    public ShopSettingsController(ShopOperatingHoursService shopOperatingHoursService) {
        this.shopOperatingHoursService = shopOperatingHoursService;
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
}
