package com.msa.shop_orders.provider.shop.dto;

import java.util.List;

public record ShopOperatingHoursData(
        Long shopLocationId,
        Integer orderCutoffMinutesBeforeClose,
        Integer closingSoonMinutes,
        List<ShopOperatingHourData> days
) {
}
