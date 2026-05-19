package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ShopOperatingHoursUpdateRequest(
        @NotEmpty(message = "Operating hours are required.")
        List<@Valid ShopOperatingHourUpdateItem> days
) {
}
