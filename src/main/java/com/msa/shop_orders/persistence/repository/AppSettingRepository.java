package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.AppSettingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSettingEntity, Long> {
    Optional<AppSettingEntity> findBySettingKey(String settingKey);
}
