package com.msa.shop_orders.internal.finance.order.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.internal.finance.order.dto.InternalFinanceOrderDtos;
import com.msa.shop_orders.internal.finance.order.service.InternalFinanceOrderSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/finance/orders")
public class InternalFinanceOrderSyncController {
    private final InternalFinanceOrderSyncService internalFinanceOrderSyncService;

    public InternalFinanceOrderSyncController(InternalFinanceOrderSyncService internalFinanceOrderSyncService) {
        this.internalFinanceOrderSyncService = internalFinanceOrderSyncService;
    }

    @PostMapping
    public ApiResponse<InternalFinanceOrderDtos.CreatedOrderData> createOrder(
            @RequestBody InternalFinanceOrderDtos.CreateOrderRequest request
    ) {
        return ApiResponse.success("Shop order created.", internalFinanceOrderSyncService.createOrder(request));
    }

    @PostMapping("/{orderId}/runtime-sync")
    public ApiResponse<Void> syncOrderRuntime(
            @PathVariable Long orderId,
            @RequestBody(required = false) InternalFinanceOrderDtos.RuntimeSyncRequest request
    ) {
        internalFinanceOrderSyncService.syncOrderRuntime(orderId, request);
        return ApiResponse.success("Shop order runtime synced.");
    }

    @PostMapping("/{orderId}/movement")
    public ApiResponse<Void> recordOrderMovement(
            @PathVariable Long orderId,
            @RequestBody InternalFinanceOrderDtos.RuntimeSyncRequest request
    ) {
        internalFinanceOrderSyncService.recordOrderMovement(orderId, request);
        return ApiResponse.success("Shop order movement recorded.");
    }

    @GetMapping("/{orderId}/context")
    public ApiResponse<InternalFinanceOrderDtos.OrderFinanceContextData> orderContext(@PathVariable Long orderId) {
        return ApiResponse.success("Shop order finance context loaded.", internalFinanceOrderSyncService.loadOrderContext(orderId));
    }

    @GetMapping("/{orderId}/items")
    public ApiResponse<java.util.List<InternalFinanceOrderDtos.OrderItemData>> orderItems(@PathVariable Long orderId) {
        return ApiResponse.success("Shop order items loaded.", internalFinanceOrderSyncService.loadOrderItems(orderId));
    }

    @PostMapping("/{orderId}/state")
    public ApiResponse<Void> updateOrderState(
            @PathVariable Long orderId,
            @RequestBody InternalFinanceOrderDtos.OrderStateUpdateRequest request
    ) {
        internalFinanceOrderSyncService.updateOrderState(orderId, request);
        return ApiResponse.success("Shop order state updated.");
    }
}
