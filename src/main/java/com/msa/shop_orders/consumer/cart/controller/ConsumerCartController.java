package com.msa.shop_orders.consumer.cart.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.consumer.cart.dto.ConsumerCartAddItemRequest;
import com.msa.shop_orders.consumer.cart.dto.ConsumerCartData;
import com.msa.shop_orders.consumer.cart.dto.ConsumerCartUpdateItemRequest;
import com.msa.shop_orders.consumer.cart.service.ConsumerCartService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop-orders/cart")
public class ConsumerCartController {
    private final ConsumerCartService consumerCartService;

    public ConsumerCartController(ConsumerCartService consumerCartService) {
        this.consumerCartService = consumerCartService;
    }

    @GetMapping
    public ApiResponse<ConsumerCartData> currentCart() {
        return ApiResponse.success(null, consumerCartService.currentCart());
    }

    @PostMapping("/items")
    public ApiResponse<ConsumerCartData> addItem(@Valid @RequestBody ConsumerCartAddItemRequest request) {
        return ApiResponse.success(null, consumerCartService.addItem(request));
    }

    @PutMapping("/items/{itemId}")
    public ApiResponse<ConsumerCartData> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestBody ConsumerCartUpdateItemRequest request
    ) {
        return ApiResponse.success(null, consumerCartService.updateItem(itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<ConsumerCartData> removeItem(@PathVariable Long itemId) {
        return ApiResponse.success(null, consumerCartService.removeItem(itemId));
    }
}
