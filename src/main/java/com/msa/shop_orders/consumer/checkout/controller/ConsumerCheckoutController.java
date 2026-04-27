package com.msa.shop_orders.consumer.checkout.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.consumer.checkout.dto.ConsumerCheckoutPreviewData;
import com.msa.shop_orders.consumer.checkout.dto.ConsumerCheckoutPreviewRequest;
import com.msa.shop_orders.consumer.checkout.service.ConsumerCheckoutService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop-orders")
public class ConsumerCheckoutController {
    private final ConsumerCheckoutService consumerCheckoutService;

    public ConsumerCheckoutController(ConsumerCheckoutService consumerCheckoutService) {
        this.consumerCheckoutService = consumerCheckoutService;
    }

    @PostMapping("/checkout-preview")
    public ApiResponse<ConsumerCheckoutPreviewData> preview(@RequestBody ConsumerCheckoutPreviewRequest request) {
        return ApiResponse.success(null, consumerCheckoutService.preview(request));
    }
}
