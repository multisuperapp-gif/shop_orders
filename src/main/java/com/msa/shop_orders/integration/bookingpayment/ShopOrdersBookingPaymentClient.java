package com.msa.shop_orders.integration.bookingpayment;

import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentApiResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentDtos.PaymentFailureRequest;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentDtos.PaymentInitiateRequest;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentDtos.PaymentInitiateResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentDtos.PaymentStatusResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentDtos.PaymentVerifyRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "shopOrdersBookingPaymentClient", url = "${app.integrations.booking-payment-url}")
public interface ShopOrdersBookingPaymentClient {
    @GetMapping("/api/v1/payments/{paymentCode}")
    ShopOrdersBookingPaymentApiResponse<PaymentStatusResponse> status(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode
    );

    @PostMapping("/api/v1/payments/{paymentCode}/initiate")
    ShopOrdersBookingPaymentApiResponse<PaymentInitiateResponse> initiate(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody(required = false) PaymentInitiateRequest request
    );

    @PostMapping("/api/v1/payments/{paymentCode}/verify")
    ShopOrdersBookingPaymentApiResponse<PaymentStatusResponse> verify(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody PaymentVerifyRequest request
    );

    @PostMapping("/api/v1/payments/{paymentCode}/failure")
    ShopOrdersBookingPaymentApiResponse<PaymentStatusResponse> failure(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentCode,
            @RequestBody(required = false) PaymentFailureRequest request
    );
}
