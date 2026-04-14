package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopLocationRepository extends JpaRepository<ShopLocationEntity, Long> {
    Optional<ShopLocationEntity> findFirstByShopIdAndPrimaryTrueOrderByIdAsc(Long shopId);
}
