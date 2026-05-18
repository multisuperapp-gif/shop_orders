package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopInventoryCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopInventoryCategoryRepository extends JpaRepository<ShopInventoryCategoryEntity, Long> {
    List<ShopInventoryCategoryEntity> findByShopIdOrderByIdAsc(Long shopId);
    boolean existsByShopIdAndShopCategoryId(Long shopId, Long shopCategoryId);
    Optional<ShopInventoryCategoryEntity> findByShopIdAndShopCategoryId(Long shopId, Long shopCategoryId);
}
