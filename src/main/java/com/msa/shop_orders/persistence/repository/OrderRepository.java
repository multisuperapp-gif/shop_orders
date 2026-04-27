package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByShopIdOrderByCreatedAtDesc(Long shopId);
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<OrderEntity> findByIdAndShopId(Long id, Long shopId);
    Optional<OrderEntity> findByIdAndUserId(Long id, Long userId);
}
