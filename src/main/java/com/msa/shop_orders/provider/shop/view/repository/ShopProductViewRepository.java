package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopProductView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ShopProductViewRepository extends MongoRepository<ShopProductView, Long> {
    List<ShopProductView> findByShopIdOrderByUpdatedAtDesc(Long shopId);
    List<ShopProductView> findByShopIdAndCategoryIdOrderByUpdatedAtDesc(Long shopId, Long categoryId);
    void deleteByShopId(Long shopId);
}
