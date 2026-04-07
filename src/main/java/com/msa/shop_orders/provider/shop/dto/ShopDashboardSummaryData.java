package com.msa.shop_orders.provider.shop.dto;

public record ShopDashboardSummaryData(
        ShopDashboardMetricData today,
        ShopDashboardMetricData monthly,
        ShopDashboardMetricData weekly,
        ShopDashboardMetricData allTime
) {
}
