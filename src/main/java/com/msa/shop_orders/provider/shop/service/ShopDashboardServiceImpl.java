package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopDashboardMetricData;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardSummaryData;
import com.msa.shop_orders.provider.shop.dto.ShopInventoryAlertData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopDashboardServiceImpl implements ShopDashboardService {
    private final ShopContextService shopContextService;
    private final ShopRuntimeViewService shopRuntimeViewService;

    public ShopDashboardServiceImpl(
            ShopContextService shopContextService,
            ShopRuntimeViewService shopRuntimeViewService
    ) {
        this.shopContextService = shopContextService;
        this.shopRuntimeViewService = shopRuntimeViewService;
    }

    @Override
    public ShopDashboardSummaryData summary() {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        return shopRuntimeViewService.loadSummary(shopEntity);
    }

    @Override
    public List<ShopOrderData> liveOrders() {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        return shopRuntimeViewService.loadLiveOrders(shopEntity);
    }

    @Override
    public List<ShopInventoryAlertData> inventoryAlerts() {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        return shopRuntimeViewService.loadInventoryAlerts(shopEntity);
    }
}
