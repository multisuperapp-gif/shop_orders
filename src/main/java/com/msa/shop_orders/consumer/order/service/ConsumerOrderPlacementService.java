package com.msa.shop_orders.consumer.order.service;

import com.msa.shop_orders.common.shoptype.ShopTypeFamilyResolver;
import com.msa.shop_orders.consumer.cart.service.ConsumerCartService;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderRequest;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderResponse;
import com.msa.shop_orders.consumer.order.type.ConsumerOrderPlacementTypeRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerOrderPlacementService {
    private final ConsumerCartService consumerCartService;
    private final ShopTypeFamilyResolver shopTypeFamilyResolver;
    private final ConsumerOrderPlacementTypeRegistry consumerOrderPlacementTypeRegistry;

    public ConsumerOrderPlacementService(
            ConsumerCartService consumerCartService,
            ShopTypeFamilyResolver shopTypeFamilyResolver,
            ConsumerOrderPlacementTypeRegistry consumerOrderPlacementTypeRegistry
    ) {
        this.consumerCartService = consumerCartService;
        this.shopTypeFamilyResolver = shopTypeFamilyResolver;
        this.consumerOrderPlacementTypeRegistry = consumerOrderPlacementTypeRegistry;
    }

    @Transactional
    public ConsumerPlaceOrderResponse placeOrder(ConsumerPlaceOrderRequest request) {
        Long shopId = consumerCartService.currentCartView().getShopId();
        return consumerOrderPlacementTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamilyByShopId(shopId))
                .placeOrder(request);
    }
}
