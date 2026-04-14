package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopDeliveryRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopDeliveryRuleRepository extends JpaRepository<ShopDeliveryRuleEntity, Long> {
    Optional<ShopDeliveryRuleEntity> findByShopLocationId(Long shopLocationId);
}
