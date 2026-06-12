package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ShopOrderData(
        Long orderId,
        String orderCode,
        String orderStatus,
        String paymentStatus,
        String customerName,
        String customerPhone,
        LocalDateTime createdAt,
        int itemCount,
        BigDecimal itemsTotal,
        BigDecimal deliveryCharges,
        BigDecimal platformFee,
        BigDecimal totalOrderValue,
        String addressLabel,
        String addressLine,
        BigDecimal deliveryLatitude,
        BigDecimal deliveryLongitude,
        String cancelReason,
        String cancelledBy,
        List<ShopOrderItemData> items
) {
}
