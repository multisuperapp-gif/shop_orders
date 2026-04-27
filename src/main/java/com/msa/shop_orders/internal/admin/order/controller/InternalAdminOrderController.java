package com.msa.shop_orders.internal.admin.order.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.internal.admin.order.dto.AdminOrderDtos;
import com.msa.shop_orders.internal.admin.order.service.InternalAdminOrderService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/orders")
public class InternalAdminOrderController {
    private final InternalAdminOrderService internalAdminOrderService;

    public InternalAdminOrderController(InternalAdminOrderService internalAdminOrderService) {
        this.internalAdminOrderService = internalAdminOrderService;
    }

    @GetMapping
    public ApiResponse<List<AdminOrderDtos.OrderSummaryData>> orders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String viewType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ApiResponse.success(null, internalAdminOrderService.orders(status, viewType, dateFrom, dateTo));
    }

    @GetMapping("/summary")
    public ApiResponse<AdminOrderDtos.OrderOperationsSummaryData> orderSummary(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String viewType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return ApiResponse.success(null, internalAdminOrderService.orderSummary(status, viewType, dateFrom, dateTo));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<AdminOrderDtos.OrderDetailData> orderDetail(@PathVariable Long orderId) {
        return ApiResponse.success(null, internalAdminOrderService.orderDetail(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<AdminOrderDtos.OrderDetailData> cancelOrder(
            @PathVariable Long orderId,
            @RequestBody AdminOrderDtos.CancelOrderRequest request
    ) {
        return ApiResponse.success("Order cancelled successfully", internalAdminOrderService.cancelOrder(orderId, request));
    }

    @PostMapping("/{orderId}/status")
    public ApiResponse<AdminOrderDtos.OrderDetailData> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestBody AdminOrderDtos.UpdateOrderStatusRequest request
    ) {
        return ApiResponse.success("Order status updated successfully", internalAdminOrderService.updateOrderStatus(orderId, request));
    }
}
