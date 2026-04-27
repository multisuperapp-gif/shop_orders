package com.msa.shop_orders.consumer.shop.dto;

import java.math.BigDecimal;
import java.util.List;

public record ConsumerShopDetailData(
        Long shopId,
        String shopCode,
        Long shopTypeId,
        String shopName,
        String approvalStatus,
        String operationalStatus,
        BigDecimal avgRating,
        Integer totalReviews,
        List<ConsumerShopCategoryData> categories
) {
}
