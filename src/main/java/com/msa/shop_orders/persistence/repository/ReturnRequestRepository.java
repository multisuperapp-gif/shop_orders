package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ReturnRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequestEntity, Long> {
    List<ReturnRequestEntity> findByOrderIdOrderByRequestedAtDescIdDesc(Long orderId);
}
