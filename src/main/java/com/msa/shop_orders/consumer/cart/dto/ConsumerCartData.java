package com.msa.shop_orders.consumer.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record ConsumerCartData(
        Long userId,
        Long shopId,
        String shopName,
        String currencyCode,
        String cartContext,
        int itemCount,
        BigDecimal subtotal,
        List<ConsumerCartItemData> items
) {
}
