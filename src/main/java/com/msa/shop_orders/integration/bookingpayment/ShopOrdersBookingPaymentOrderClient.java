package com.msa.shop_orders.integration.bookingpayment;

import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentApiResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentOrderDtos.CancelShopOrderRequest;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentOrderDtos.UpdateShopOrderStatusRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "shopOrdersBookingPaymentOrderClient", url = "${app.integrations.booking-payment-url}")
public interface ShopOrdersBookingPaymentOrderClient {
    @PostMapping("/shop-orders/cancel")
    ShopOrdersBookingPaymentApiResponse<Void> cancelByUser(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody CancelShopOrderRequest request
    );

    @PostMapping("/shop-orders/status")
    ShopOrdersBookingPaymentApiResponse<Object> updateStatus(
            @RequestBody UpdateShopOrderStatusRequest request
    );
}
