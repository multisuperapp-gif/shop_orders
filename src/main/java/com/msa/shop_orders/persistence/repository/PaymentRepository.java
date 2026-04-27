package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByPaymentCode(String paymentCode);
    Optional<PaymentEntity> findFirstByPayableTypeAndPayableIdOrderByIdDesc(String payableType, Long payableId);
    List<PaymentEntity> findByPayableTypeAndPayableIdIn(String payableType, Collection<Long> payableIds);
}
