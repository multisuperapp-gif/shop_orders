package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShopOperatingHourUpdateItem(
        @NotNull(message = "Weekday is required.")
        @Min(value = 1, message = "Weekday must be between 1 and 7.")
        @Max(value = 7, message = "Weekday must be between 1 and 7.")
        Integer weekday,
        boolean closed,
        String openTime,
        String closeTime
) {
}
