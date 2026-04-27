package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopLocationView;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShopLocationViewRepository extends MongoRepository<ShopLocationView, Long> {
    Optional<ShopLocationView> findFirstByShopIdAndPrimaryTrue(Long shopId);
}
