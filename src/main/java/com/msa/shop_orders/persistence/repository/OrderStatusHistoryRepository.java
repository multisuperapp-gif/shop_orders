package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Long> {
    List<OrderStatusHistoryEntity> findByOrderIdOrderByChangedAtAscIdAsc(Long orderId);
    List<OrderStatusHistoryEntity> findByOrderIdInOrderByChangedAtAscIdAsc(Collection<Long> orderIds);
    Optional<OrderStatusHistoryEntity> findFirstByOrderIdAndNewStatusOrderByChangedAtDescIdDesc(Long orderId, String newStatus);
}
