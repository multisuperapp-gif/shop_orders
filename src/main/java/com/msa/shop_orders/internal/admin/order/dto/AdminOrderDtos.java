package com.msa.shop_orders.internal.admin.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminOrderDtos {
    private AdminOrderDtos() {
    }

    public record OrderSummaryData(
            Long orderId,
            Long addressId,
            Long shopId,
            String orderCode,
            String orderStatus,
            String customerPublicUserId,
            String customerPhone,
            String shopName,
            BigDecimal totalAmount,
            LocalDateTime createdAt
    ) {
    }

    public record OrderOperationsSummaryData(
            long totalLiveOrders,
            long inProgressOrders,
            long completedOrders,
            long outForDeliveryOrders
    ) {
    }

    public record OrderItemData(
            Long variantId,
            String productName,
            String variantName,
            Integer quantity,
            BigDecimal unitPriceSnapshot,
            BigDecimal taxSnapshot,
            BigDecimal lineTotal
    ) {
    }

    public record ReturnRequestData(
            Long orderItemId,
            String reason,
            String status,
            LocalDateTime requestedAt
    ) {
    }

    public record CancelOrderRequest(
            String reason,
            String refundPolicyApplied,
            Long changedByUserId
    ) {
    }

    public record UpdateOrderStatusRequest(
            String newStatus,
            String reason,
            Long changedByUserId
    ) {
    }

    public record OrderDetailData(
            Long orderId,
            Long addressId,
            String orderCode,
            String orderStatus,
            String customerPublicUserId,
            String customerPhone,
            Long shopId,
            String shopName,
            String deliveryAddress,
            BigDecimal subtotalAmount,
            BigDecimal taxAmount,
            BigDecimal deliveryFee,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            String currencyCode,
            LocalDateTime createdAt,
            String cancellationReason,
            String refundPolicyApplied,
            List<OrderItemData> items,
            List<ReturnRequestData> returns
    ) {
    }
}
