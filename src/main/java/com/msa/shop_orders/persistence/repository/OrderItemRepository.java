package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Long> {
    List<OrderItemEntity> findByOrderIdIn(Collection<Long> orderIds);
    List<OrderItemEntity> findByOrderIdOrderByIdAsc(Long orderId);
}
