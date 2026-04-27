package com.msa.shop_orders.consumer.order.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.consumer.order.dto.ConsumerCancelOrderRequest;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderDetailData;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderSummaryData;
import com.msa.shop_orders.consumer.order.service.ConsumerOrderLifecycleService;
import com.msa.shop_orders.consumer.order.service.ConsumerOrderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop-orders")
public class ConsumerOrderController {
    private final ConsumerOrderService consumerOrderService;
    private final ConsumerOrderLifecycleService consumerOrderLifecycleService;

    public ConsumerOrderController(
            ConsumerOrderService consumerOrderService,
            ConsumerOrderLifecycleService consumerOrderLifecycleService
    ) {
        this.consumerOrderService = consumerOrderService;
        this.consumerOrderLifecycleService = consumerOrderLifecycleService;
    }

    @GetMapping("/orders")
    public ApiResponse<List<ConsumerOrderSummaryData>> orders() {
        return ApiResponse.success(null, consumerOrderService.orders());
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<ConsumerOrderDetailData> orderDetail(@PathVariable Long orderId) {
        return ApiResponse.success(null, consumerOrderService.orderDetail(orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<Void> cancel(
            @PathVariable Long orderId,
            @RequestBody(required = false) ConsumerCancelOrderRequest request
    ) {
        consumerOrderLifecycleService.cancel(orderId, request == null ? null : request.reason());
        return ApiResponse.success("Shop order cancelled successfully");
    }
}
