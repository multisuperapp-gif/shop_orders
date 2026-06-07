package com.msa.shop_orders.integration.bookingpayment.dto;

public final class ShopOrdersBookingPaymentOrderDtos {
    private ShopOrdersBookingPaymentOrderDtos() {
    }

    public record CancelShopOrderRequest(
            Long orderId,
            Long userId,
            String reason
    ) {
    }

    public record UpdateShopOrderStatusRequest(
            Long orderId,
            String newStatus,
            Long changedByUserId,
            String reason,
            String refundPolicyApplied
    ) {
    }

    public record NotifyShopOrderEventRequest(
            String event,
            Long shopId,
            Long userId,
            Long orderId,
            String orderCode,
            String message
    ) {
    }
}
