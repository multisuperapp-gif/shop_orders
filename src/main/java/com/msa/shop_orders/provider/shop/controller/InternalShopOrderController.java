package com.msa.shop_orders.provider.shop.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardSummaryData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.service.ShopOrderReadService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/shops")
public class InternalShopOrderController {
    private final ShopOrderReadService shopOrderReadService;

    public InternalShopOrderController(ShopOrderReadService shopOrderReadService) {
        this.shopOrderReadService = shopOrderReadService;
    }

    @GetMapping("/{shopId}/dashboard/summary")
    public ApiResponse<ShopDashboardSummaryData> summary(@PathVariable Long shopId) {
        return ApiResponse.success(null, shopOrderReadService.summary(shopId));
    }

    @GetMapping("/{shopId}/dashboard/live-orders")
    public ApiResponse<List<ShopOrderData>> liveOrders(@PathVariable Long shopId) {
        return ApiResponse.success(null, shopOrderReadService.liveOrders(shopId));
    }

    @GetMapping("/{shopId}/orders")
    public ApiResponse<List<ShopOrderData>> orders(
            @PathVariable Long shopId,
            @RequestParam(required = false) String dateFilter,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return ApiResponse.success(null, shopOrderReadService.orders(shopId, dateFilter, status, fromDate, toDate));
    }
}
