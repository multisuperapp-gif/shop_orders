package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, Long> {
    Optional<ProductImageEntity> findFirstByProductIdAndPrimaryImageTrue(Long productId);
    List<ProductImageEntity> findByProductIdIn(Collection<Long> productIds);
    List<ProductImageEntity> findByProductIdOrderBySortOrderAscIdAsc(Long productId);
    void deleteByProductId(Long productId);
}
