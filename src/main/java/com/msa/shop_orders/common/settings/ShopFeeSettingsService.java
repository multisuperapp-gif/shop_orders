package com.msa.shop_orders.common.settings;

import com.msa.shop_orders.persistence.repository.AppSettingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Resolves shop pricing fees from the shared {@code app_settings} table.
 * The platform fee is a percentage of the item subtotal, configured under the
 * {@code platform.fee.shop} key and added on top of the order total.
 */
@Service
public class ShopFeeSettingsService {
    private static final String PLATFORM_FEE_SHOP = "platform.fee.shop";
    private static final String COMMISSION_FEE_SHOP = "commission.fee.shop";

    private final AppSettingRepository appSettingRepository;

    public ShopFeeSettingsService(AppSettingRepository appSettingRepository) {
        this.appSettingRepository = appSettingRepository;
    }

    /** Platform fee percentage (e.g. 1.00 means 1%). Defaults to 0 when unset. */
    public BigDecimal shopPlatformFeePercent() {
        return appSettingRepository.findBySettingKey(PLATFORM_FEE_SHOP)
                .map(setting -> parseAmount(setting.getSettingValue()))
                .orElse(BigDecimal.ZERO);
    }

    /** Platform commission percentage charged to the shop on the item subtotal. */
    public BigDecimal shopCommissionPercent() {
        return appSettingRepository.findBySettingKey(COMMISSION_FEE_SHOP)
                .map(setting -> parseAmount(setting.getSettingValue()))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Shop's net earning for an order's item subtotal: subtotal minus the
     * platform commission. (Platform fee + delivery are not the shop's.)
     */
    public BigDecimal shopNetEarning(BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal percent = shopCommissionPercent();
        if (percent.signum() <= 0) {
            return subtotal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal commission = subtotal
                .multiply(percent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return subtotal.subtract(commission).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Platform fee amount for the given subtotal, rounded to 2 decimals. */
    public BigDecimal shopPlatformFeeAmount(BigDecimal subtotal) {
        BigDecimal percent = shopPlatformFeePercent();
        if (subtotal == null || subtotal.signum() <= 0 || percent.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return subtotal
                .multiply(percent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseAmount(String value) {
        try {
            BigDecimal parsed = new BigDecimal(value == null ? "" : value.trim());
            return parsed.signum() >= 0 ? parsed : BigDecimal.ZERO;
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }
}
