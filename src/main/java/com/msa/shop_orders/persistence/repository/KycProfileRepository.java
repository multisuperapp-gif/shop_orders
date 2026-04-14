package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.KycProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KycProfileRepository extends JpaRepository<KycProfileEntity, Long> {
    Optional<KycProfileEntity> findByUser_IdAndKycType(Long userId, String kycType);
}
