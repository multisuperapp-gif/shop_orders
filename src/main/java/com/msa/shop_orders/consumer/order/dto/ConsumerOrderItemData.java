package com.msa.shop_orders.consumer.order.dto;

import java.math.BigDecimal;

public record ConsumerOrderItemData(
        Long productId,
        Long variantId,
        String productName,
        String variantName,
        Long imageFileId,
        Integer quantity,
        String unitLabel,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
