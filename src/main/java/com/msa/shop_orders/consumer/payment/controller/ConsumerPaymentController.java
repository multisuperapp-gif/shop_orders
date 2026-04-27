package com.msa.shop_orders.consumer.payment.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.consumer.payment.dto.ConsumerPaymentDtos;
import com.msa.shop_orders.consumer.payment.service.ConsumerPaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop-orders/payments")
public class ConsumerPaymentController {
    private final ConsumerPaymentService consumerPaymentService;

    public ConsumerPaymentController(ConsumerPaymentService consumerPaymentService) {
        this.consumerPaymentService = consumerPaymentService;
    }

    @GetMapping("/{paymentCode}")
    public ApiResponse<ConsumerPaymentDtos.PaymentStatusData> status(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String paymentCode
    ) {
        return ApiResponse.success(null, consumerPaymentService.status(authorizationHeader, paymentCode));
    }

    @PostMapping("/{paymentCode}/initiate")
    public ApiResponse<ConsumerPaymentDtos.PaymentInitiateData> initiate(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String paymentCode,
            @RequestBody(required = false) ConsumerPaymentDtos.PaymentInitiateRequest request
    ) {
        return ApiResponse.success(null, consumerPaymentService.initiate(authorizationHeader, paymentCode, request));
    }

    @PostMapping("/{paymentCode}/verify")
    public ApiResponse<ConsumerPaymentDtos.PaymentStatusData> verify(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String paymentCode,
            @RequestBody ConsumerPaymentDtos.PaymentVerifyRequest request
    ) {
        return ApiResponse.success(null, consumerPaymentService.verify(authorizationHeader, paymentCode, request));
    }

    @PostMapping("/{paymentCode}/failure")
    public ApiResponse<ConsumerPaymentDtos.PaymentStatusData> failure(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String paymentCode,
            @RequestBody(required = false) ConsumerPaymentDtos.PaymentFailureRequest request
    ) {
        return ApiResponse.success(null, consumerPaymentService.failure(authorizationHeader, paymentCode, request));
    }
}
