package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopOperatingHoursEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopOperatingHoursRepository extends JpaRepository<ShopOperatingHoursEntity, Long> {
    Optional<ShopOperatingHoursEntity> findFirstByShopLocationIdAndWeekday(Long shopLocationId, Integer weekday);
}
