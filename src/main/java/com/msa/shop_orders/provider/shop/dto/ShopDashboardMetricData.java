package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;

public record ShopDashboardMetricData(
        long orders,
        long completed,
        long cancelled,
        BigDecimal orderValue
) {
}
