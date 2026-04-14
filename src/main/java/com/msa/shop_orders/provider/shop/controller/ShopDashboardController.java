package com.msa.shop_orders.provider.shop.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardSummaryData;
import com.msa.shop_orders.provider.shop.dto.ShopInventoryAlertData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.service.ShopDashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shops/dashboard")
public class ShopDashboardController {
    private final ShopDashboardService shopDashboardService;

    public ShopDashboardController(ShopDashboardService shopDashboardService) {
        this.shopDashboardService = shopDashboardService;
    }

    @GetMapping("/summary")
    public ApiResponse<ShopDashboardSummaryData> summary() {
        return ApiResponse.success(null, shopDashboardService.summary());
    }

    @GetMapping("/live-orders")
    public ApiResponse<List<ShopOrderData>> liveOrders() {
        return ApiResponse.success(null, shopDashboardService.liveOrders());
    }

    @GetMapping("/inventory-alerts")
    public ApiResponse<List<ShopInventoryAlertData>> inventoryAlerts() {
        return ApiResponse.success(null, shopDashboardService.inventoryAlerts());
    }
}
