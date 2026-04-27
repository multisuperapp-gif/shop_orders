package com.msa.shop_orders.consumer.shop.dto;

import java.math.BigDecimal;

public record ConsumerShopSummaryData(
        Long shopId,
        String shopCode,
        Long shopTypeId,
        String shopName,
        String approvalStatus,
        String operationalStatus,
        BigDecimal avgRating,
        Integer totalReviews
) {
}
