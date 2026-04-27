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
}
