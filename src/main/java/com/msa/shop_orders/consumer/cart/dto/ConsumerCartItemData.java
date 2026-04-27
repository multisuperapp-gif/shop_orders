package com.msa.shop_orders.consumer.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record ConsumerCartItemData(
        Long itemId,
        String lineKey,
        Long productId,
        Long variantId,
        String productName,
        String variantName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String imageObjectKey,
        List<String> selectedOptions,
        String cookingRequest
) {
}
