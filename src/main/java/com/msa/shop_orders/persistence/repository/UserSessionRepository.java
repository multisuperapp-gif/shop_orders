package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.UserSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, Long> {
    Optional<UserSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    Optional<UserSessionEntity> findByIdAndUser_Id(Long id, Long userId);

    List<UserSessionEntity> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
