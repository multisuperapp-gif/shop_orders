package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopProductView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShopProductViewRepository extends MongoRepository<ShopProductView, Long> {
    List<ShopProductView> findByShopIdOrderByUpdatedAtDesc(Long shopId);
    List<ShopProductView> findByShopIdAndCategoryIdOrderByUpdatedAtDesc(Long shopId, Long categoryId);
    List<ShopProductView> findByVariantsVariantIdIn(Collection<Long> variantIds);
    Optional<ShopProductView> findBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCase(String sku);
    void deleteByShopId(Long shopId);
}
