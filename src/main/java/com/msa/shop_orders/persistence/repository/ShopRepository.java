package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopRepository extends JpaRepository<ShopEntity, Long> {
    Optional<ShopEntity> findFirstByOwnerUserIdOrderByIdDesc(Long ownerUserId);
}
