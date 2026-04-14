package com.msa.shop_orders.provider.common.dto;

import java.util.List;

public record ProfileStatusResponseData(
        String profileStatus,
        String approvalStatus,
        List<String> missingFields
) {
}
