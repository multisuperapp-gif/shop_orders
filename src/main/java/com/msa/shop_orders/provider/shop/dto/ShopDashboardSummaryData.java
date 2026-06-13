package com.msa.shop_orders.provider.shop.dto;

import java.math.BigDecimal;

public record ShopDashboardSummaryData(
        ShopDashboardMetricData today,
        ShopDashboardMetricData monthly,
        ShopDashboardMetricData weekly,
        ShopDashboardMetricData allTime,
        // Shop's overall customer rating + number of reviews, for the home card.
        BigDecimal avgRating,
        int totalReviews
) {
}
