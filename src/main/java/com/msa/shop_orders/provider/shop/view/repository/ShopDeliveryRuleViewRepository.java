package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopDeliveryRuleView;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShopDeliveryRuleViewRepository extends MongoRepository<ShopDeliveryRuleView, Long> {
}
