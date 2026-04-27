package com.msa.shop_orders.consumer.shop.dto;

public record ConsumerShopTypeData(
        Long shopTypeId,
        String name,
        Integer sortOrder
) {
}
