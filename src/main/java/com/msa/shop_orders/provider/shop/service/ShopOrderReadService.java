package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopDashboardSummaryData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;

import java.time.LocalDate;
import java.util.List;

public interface ShopOrderReadService {
    ShopDashboardSummaryData summary(Long shopId);
    List<ShopOrderData> liveOrders(Long shopId);
    List<ShopOrderData> orders(Long shopId, String dateFilter, String status, LocalDate fromDate, LocalDate toDate);
}
