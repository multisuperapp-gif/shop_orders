package com.msa.shop_orders.consumer.shop.dto;

public record ConsumerShopCategoryData(
        Long categoryId,
        Long shopTypeId,
        String name,
        String normalizedName,
        Integer sortOrder
) {
}
