package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;

public record ShopOrderItemData(
        String itemName,
        Integer quantity,
        String unitLabel,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}
