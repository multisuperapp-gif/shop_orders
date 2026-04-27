package com.msa.shop_orders.provider.shop.type.order;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderShopOrderTypeRegistry {
    private final Map<ShopTypeFamily, ProviderShopOrderTypeHandler> handlers;

    public ProviderShopOrderTypeRegistry(List<ProviderShopOrderTypeHandler> handlers) {
        this.handlers = new EnumMap<>(ShopTypeFamily.class);
        for (ProviderShopOrderTypeHandler handler : handlers) {
            this.handlers.put(handler.family(), handler);
        }
    }

    public ProviderShopOrderTypeHandler resolve(ShopTypeFamily family) {
        return handlers.getOrDefault(family, handlers.get(ShopTypeFamily.SHARED));
    }
}
