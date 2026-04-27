package com.msa.shop_orders.provider.shop.type.order.gift;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.type.order.ProviderShopOrderTypeHandler;
import com.msa.shop_orders.provider.shop.type.order.shared.SharedProviderShopOrderTypeHandler;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class GiftProviderShopOrderTypeHandler implements ProviderShopOrderTypeHandler {
    private final SharedProviderShopOrderTypeHandler delegate;
    public GiftProviderShopOrderTypeHandler(SharedProviderShopOrderTypeHandler delegate) { this.delegate = delegate; }
    public ShopTypeFamily family() { return ShopTypeFamily.GIFT; }
    public List<ShopOrderData> orders(ShopShellView shop, String dateFilter, String status, LocalDate fromDate, LocalDate toDate) { return delegate.orders(shop, dateFilter, status, fromDate, toDate); }
    public ShopOrderData updateOrderStatus(ShopShellView shop, Long orderId, ShopOrderStatusUpdateRequest request) { return delegate.updateOrderStatus(shop, orderId, request); }
}
