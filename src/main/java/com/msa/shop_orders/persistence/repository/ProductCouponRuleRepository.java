package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ProductCouponRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductCouponRuleRepository extends JpaRepository<ProductCouponRuleEntity, Long> {
    Optional<ProductCouponRuleEntity> findFirstByProductIdOrderByIdDesc(Long productId);
    List<ProductCouponRuleEntity> findByProductIdIn(Collection<Long> productIds);
    void deleteByProductId(Long productId);
}
