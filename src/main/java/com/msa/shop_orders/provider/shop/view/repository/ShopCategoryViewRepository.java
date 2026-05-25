package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShopCategoryViewRepository extends MongoRepository<ShopCategoryView, String> {
    List<ShopCategoryView> findByShopTypeIdAndShopIdIsNullAndEnabledTrue(Long shopTypeId);
    long countByShopTypeIdAndShopIdIsNull(Long shopTypeId);
    List<ShopCategoryView> findByShopId(Long shopId);
    List<ShopCategoryView> findByShopIdAndEnabledTrue(Long shopId);
    long countByShopId(Long shopId);
    Optional<ShopCategoryView> findFirstByShopIdIsNullAndNormalizedNameIgnoreCase(String normalizedName);
    Optional<ShopCategoryView> findByShopTypeIdAndShopIdIsNullAndCategoryId(Long shopTypeId, Long categoryId);
    Optional<ShopCategoryView> findByShopIdAndCategoryId(Long shopId, Long categoryId);
}
