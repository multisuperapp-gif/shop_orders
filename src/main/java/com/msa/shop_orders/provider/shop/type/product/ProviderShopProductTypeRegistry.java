package com.msa.shop_orders.provider.shop.type.product;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderShopProductTypeRegistry {
    private final Map<ShopTypeFamily, ProviderShopProductTypeHandler> handlers;

    public ProviderShopProductTypeRegistry(List<ProviderShopProductTypeHandler> handlers) {
        this.handlers = new EnumMap<>(ShopTypeFamily.class);
        for (ProviderShopProductTypeHandler handler : handlers) {
            this.handlers.put(handler.family(), handler);
        }
    }

    public ProviderShopProductTypeHandler resolve(ShopTypeFamily family) {
        return handlers.getOrDefault(family, handlers.get(ShopTypeFamily.SHARED));
    }
}
