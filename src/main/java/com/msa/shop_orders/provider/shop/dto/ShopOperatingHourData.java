package com.msa.shop_orders.provider.shop.dto;

public record ShopOperatingHourData(
        Integer weekday,
        boolean closed,
        String openTime,
        String closeTime
) {
}
