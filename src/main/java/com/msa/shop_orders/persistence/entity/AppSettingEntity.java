package com.msa.shop_orders.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Read-only mapping of the shared {@code app_settings} table (owned/seeded by
 * other services such as booking-payment). shop-orders only reads platform-fee
 * style settings from here; it never writes to this table.
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
public class AppSettingEntity {
    @Id
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 120)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String settingValue;

    @Column(name = "description", length = 255)
    private String description;
}
