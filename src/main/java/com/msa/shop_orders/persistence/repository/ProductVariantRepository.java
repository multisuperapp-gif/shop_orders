package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, Long> {
    Optional<ProductVariantEntity> findByProductIdAndDefaultVariantTrue(Long productId);
    List<ProductVariantEntity> findByProductIdIn(Collection<Long> productIds);
    List<ProductVariantEntity> findByProductIdOrderBySortOrderAscIdAsc(Long productId);
}
