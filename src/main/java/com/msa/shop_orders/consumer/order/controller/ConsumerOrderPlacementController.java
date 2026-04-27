package com.msa.shop_orders.consumer.order.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderRequest;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderResponse;
import com.msa.shop_orders.consumer.order.service.ConsumerOrderPlacementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop-orders/orders")
public class ConsumerOrderPlacementController {
    private final ConsumerOrderPlacementService consumerOrderPlacementService;

    public ConsumerOrderPlacementController(ConsumerOrderPlacementService consumerOrderPlacementService) {
        this.consumerOrderPlacementService = consumerOrderPlacementService;
    }

    @PostMapping("/place")
    public ApiResponse<ConsumerPlaceOrderResponse> place(@Valid @RequestBody ConsumerPlaceOrderRequest request) {
        return ApiResponse.success("Order created", consumerOrderPlacementService.placeOrder(request));
    }
}
