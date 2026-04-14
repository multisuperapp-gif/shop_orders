package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ProductPromotionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductPromotionRepository extends JpaRepository<ProductPromotionEntity, Long> {
    Optional<ProductPromotionEntity> findFirstByProductIdOrderByIdDesc(Long productId);
    List<ProductPromotionEntity> findByProductIdIn(Collection<Long> productIds);
    void deleteByProductId(Long productId);
}
