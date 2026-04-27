package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShopCategoryViewRepository extends MongoRepository<ShopCategoryView, String> {
    List<ShopCategoryView> findByShopTypeIdAndShopIdIsNullAndEnabledTrue(Long shopTypeId);
    long countByShopTypeIdAndShopIdIsNull(Long shopTypeId);
    List<ShopCategoryView> findByShopIdAndEnabledTrue(Long shopId);
    long countByShopId(Long shopId);
}
