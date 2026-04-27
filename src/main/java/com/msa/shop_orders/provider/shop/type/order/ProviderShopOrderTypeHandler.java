package com.msa.shop_orders.provider.shop.type.order;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.view.ShopShellView;

import java.time.LocalDate;
import java.util.List;

public interface ProviderShopOrderTypeHandler {
    ShopTypeFamily family();
    List<ShopOrderData> orders(ShopShellView shop, String dateFilter, String status, LocalDate fromDate, LocalDate toDate);
    ShopOrderData updateOrderStatus(ShopShellView shop, Long orderId, ShopOrderStatusUpdateRequest request);
}
