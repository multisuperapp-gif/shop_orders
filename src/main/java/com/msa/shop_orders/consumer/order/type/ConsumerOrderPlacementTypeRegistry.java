package com.msa.shop_orders.consumer.order.type;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ConsumerOrderPlacementTypeRegistry {
    private final Map<ShopTypeFamily, ConsumerOrderPlacementTypeHandler> handlers;

    public ConsumerOrderPlacementTypeRegistry(List<ConsumerOrderPlacementTypeHandler> handlers) {
        this.handlers = new EnumMap<>(ShopTypeFamily.class);
        for (ConsumerOrderPlacementTypeHandler handler : handlers) {
            this.handlers.put(handler.family(), handler);
        }
    }

    public ConsumerOrderPlacementTypeHandler resolve(ShopTypeFamily family) {
        return handlers.getOrDefault(family, handlers.get(ShopTypeFamily.SHARED));
    }
}
