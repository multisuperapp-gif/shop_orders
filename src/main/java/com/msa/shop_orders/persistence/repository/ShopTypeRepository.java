package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopTypeRepository extends JpaRepository<ShopTypeEntity, Long> {
    Optional<ShopTypeEntity> findByIdAndActiveTrue(Long id);
    List<ShopTypeEntity> findByActiveTrueOrderBySortOrderAscNameAsc();
}
