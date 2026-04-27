package com.msa.shop_orders.consumer.order.type.footwear;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderRequest;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderResponse;
import com.msa.shop_orders.consumer.order.type.ConsumerOrderPlacementTypeHandler;
import com.msa.shop_orders.consumer.order.type.shared.SharedConsumerOrderPlacementTypeHandler;
import org.springframework.stereotype.Component;

@Component
public class FootwearConsumerOrderPlacementTypeHandler implements ConsumerOrderPlacementTypeHandler {
    private final SharedConsumerOrderPlacementTypeHandler delegate;
    public FootwearConsumerOrderPlacementTypeHandler(SharedConsumerOrderPlacementTypeHandler delegate) { this.delegate = delegate; }
    public ShopTypeFamily family() { return ShopTypeFamily.FOOTWEAR; }
    public ConsumerPlaceOrderResponse placeOrder(ConsumerPlaceOrderRequest request) { return delegate.placeOrder(request); }
}
