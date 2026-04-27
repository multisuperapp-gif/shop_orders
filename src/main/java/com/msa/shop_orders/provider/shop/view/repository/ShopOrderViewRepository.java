package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ShopOrderViewRepository extends MongoRepository<ShopOrderView, Long> {
    List<ShopOrderView> findByShopIdOrderByCreatedAtDesc(Long shopId);
    List<ShopOrderView> findByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByShopId(Long shopId);
}
