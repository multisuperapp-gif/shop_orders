package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByShopIdOrderByCreatedAtDesc(Long shopId);
}
