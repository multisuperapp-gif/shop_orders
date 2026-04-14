package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryEntity, Long> {
    Optional<InventoryEntity> findByVariantId(Long variantId);
    List<InventoryEntity> findByVariantIdIn(Collection<Long> variantIds);
}
