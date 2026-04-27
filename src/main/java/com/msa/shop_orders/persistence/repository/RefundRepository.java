package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<RefundEntity, Long> {
    Optional<RefundEntity> findFirstByPaymentIdOrderByIdDesc(Long paymentId);
    List<RefundEntity> findByPaymentIdInOrderByIdDesc(Collection<Long> paymentIds);
}
