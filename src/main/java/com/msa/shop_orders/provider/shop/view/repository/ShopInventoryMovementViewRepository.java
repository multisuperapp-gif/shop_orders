package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopInventoryMovementView;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShopInventoryMovementViewRepository extends MongoRepository<ShopInventoryMovementView, String> {
}
