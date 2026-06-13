package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;

public record ShopDashboardMetricData(
        long orders,
        long completed,
        long cancelled,
        // Gross value of every order in the period (kept for reference).
        BigDecimal orderValue,
        // Actual earnings: paid orders that were not cancelled/rejected.
        BigDecimal earnings
) {
}
