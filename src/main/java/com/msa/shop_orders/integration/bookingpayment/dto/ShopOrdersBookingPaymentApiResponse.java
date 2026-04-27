package com.msa.shop_orders.integration.bookingpayment.dto;

public record ShopOrdersBookingPaymentApiResponse<T>(
        boolean success,
        String message,
        String errorCode,
        T data
) {
}
