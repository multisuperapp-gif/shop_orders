package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopInventoryAlertData;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardSummaryData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;

import java.util.List;

public interface ShopDashboardService {
    ShopDashboardSummaryData summary();
    List<ShopOrderData> liveOrders();
    List<ShopInventoryAlertData> inventoryAlerts();
}
