package com.msa.shop_orders.consumer.shop.type;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ConsumerShopTypeRegistry {
    private final Map<ShopTypeFamily, ConsumerShopTypeHandler> handlers;

    public ConsumerShopTypeRegistry(List<ConsumerShopTypeHandler> handlers) {
        this.handlers = new EnumMap<>(ShopTypeFamily.class);
        for (ConsumerShopTypeHandler handler : handlers) {
            this.handlers.put(handler.family(), handler);
        }
    }

    public ConsumerShopTypeHandler resolve(ShopTypeFamily family) {
        return handlers.getOrDefault(family, handlers.get(ShopTypeFamily.SHARED));
    }
}
