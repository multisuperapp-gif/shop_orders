package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopCategoryRepository extends JpaRepository<ShopCategoryEntity, Long> {
    List<ShopCategoryEntity> findByActiveTrueOrderByNameAsc();
    Optional<ShopCategoryEntity> findByNormalizedNameIgnoreCase(String normalizedName);
}
