package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopTypeCategoryMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopTypeCategoryMappingRepository extends JpaRepository<ShopTypeCategoryMappingEntity, Long> {
    List<ShopTypeCategoryMappingEntity> findByShopTypeIdAndActiveTrueOrderByIdAsc(Long shopTypeId);
    boolean existsByShopTypeIdAndShopCategoryIdAndActiveTrue(Long shopTypeId, Long shopCategoryId);
}
