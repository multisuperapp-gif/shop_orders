package com.msa.shop_orders.consumer.order.type;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderRequest;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderResponse;

public interface ConsumerOrderPlacementTypeHandler {
    ShopTypeFamily family();
    ConsumerPlaceOrderResponse placeOrder(ConsumerPlaceOrderRequest request);
}
