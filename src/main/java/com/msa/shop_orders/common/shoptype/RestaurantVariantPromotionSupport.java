package com.msa.shop_orders.common.shoptype;

import com.msa.shop_orders.provider.shop.view.ShopProductView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RestaurantVariantPromotionSupport {
    private RestaurantVariantPromotionSupport() {
    }

    public static boolean hasActivePromotion(ShopProductView product) {
        if (hasVariantSpecificPromotion(product)) {
            List<ShopProductView.Variant> variants = product == null ? null : product.getVariants();
            if (variants == null) {
                return false;
            }
            return variants.stream().anyMatch(RestaurantVariantPromotionSupport::hasActiveVariantPromotion);
        }
        if (product == null || product.getPromotion() == null) {
            return false;
        }
        ShopProductView.Promotion promotion = product.getPromotion();
        if (!"ACTIVE".equalsIgnoreCase(promotion.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return promotion.getStartsAt() != null
                && promotion.getEndsAt() != null
                && !now.isBefore(promotion.getStartsAt())
                && !now.isAfter(promotion.getEndsAt());
    }

    public static boolean hasActivePromotion(ShopProductView product, ShopProductView.Variant variant) {
        if (variant == null) {
            return false;
        }
        if (hasVariantSpecificPromotion(product)) {
            return hasActiveVariantPromotion(variant);
        }
        if (!hasActivePromotion(product)) {
            return false;
        }
        return defaultAmount(product.getPromotion().getPaidAmount()).compareTo(BigDecimal.ZERO) > 0;
    }

    public static BigDecimal resolveEffectiveSellingPrice(ShopProductView product, ShopProductView.Variant variant) {
        BigDecimal baseSellingPrice = variant == null
                ? BigDecimal.ZERO
                : defaultAmount(variant.getSellingPrice());
        if (!hasActivePromotion(product, variant)) {
            return baseSellingPrice;
        }
        if (hasVariantSpecificPromotion(product)) {
            BigDecimal variantPromotion = variantPromotionPaidAmount(variant);
            return variantPromotion == null ? baseSellingPrice : variantPromotion;
        }
        BigDecimal paidAmount = defaultAmount(product.getPromotion().getPaidAmount());
        return paidAmount.compareTo(BigDecimal.ZERO) > 0 ? paidAmount : baseSellingPrice;
    }

    public static BigDecimal resolveDisplayOriginalPrice(ShopProductView product, ShopProductView.Variant variant) {
        BigDecimal baseSellingPrice = variant == null
                ? BigDecimal.ZERO
                : defaultAmount(variant.getSellingPrice());
        BigDecimal mrp = variant == null ? BigDecimal.ZERO : defaultAmount(variant.getMrp());
        if (hasActivePromotion(product, variant) && baseSellingPrice.compareTo(BigDecimal.ZERO) > 0) {
            return baseSellingPrice;
        }
        return mrp.compareTo(BigDecimal.ZERO) > 0 ? mrp : baseSellingPrice;
    }

    private static boolean hasVariantSpecificPromotion(ShopProductView product) {
        if (product == null) {
            return false;
        }
        List<ShopProductView.Variant> variants = product.getVariants();
        return variants != null && variants.size() > 1;
    }

    private static boolean hasActiveVariantPromotion(ShopProductView.Variant variant) {
        if (!variantPromotionEnabled(variant)) {
            return false;
        }
        BigDecimal paidAmount = variantPromotionPaidAmount(variant);
        if (paidAmount == null) {
            return false;
        }
        LocalDateTime startsAt = variantPromotionDate(variant, "promotionStartsAt");
        LocalDateTime endsAt = variantPromotionDate(variant, "promotionEndsAt");
        LocalDateTime now = LocalDateTime.now();
        return startsAt != null
                && endsAt != null
                && !now.isBefore(startsAt)
                && !now.isAfter(endsAt);
    }

    private static boolean variantPromotionEnabled(ShopProductView.Variant variant) {
        if (variant == null || variant.getAttributes() == null) {
            return false;
        }
        Object raw = variant.getAttributes().get("promotionEnabled");
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String value) {
            return "true".equalsIgnoreCase(value.trim());
        }
        return false;
    }

    private static BigDecimal variantPromotionPaidAmount(ShopProductView.Variant variant) {
        if (variant == null) {
            return null;
        }
        Map<String, Object> attributes = variant.getAttributes();
        if (attributes == null) {
            return null;
        }
        Object raw = attributes.get("promotionPaidAmount");
        if (raw instanceof BigDecimal value) {
            return normalizePromotionPrice(value, variant);
        }
        if (raw instanceof Number value) {
            return normalizePromotionPrice(BigDecimal.valueOf(value.doubleValue()), variant);
        }
        if (raw instanceof String value) {
            try {
                return normalizePromotionPrice(new BigDecimal(value.trim()), variant);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static LocalDateTime variantPromotionDate(ShopProductView.Variant variant, String key) {
        if (variant == null || variant.getAttributes() == null) {
            return null;
        }
        Object raw = variant.getAttributes().get(key);
        if (raw instanceof LocalDateTime value) {
            return value;
        }
        if (raw instanceof String value) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal normalizePromotionPrice(BigDecimal value, ShopProductView.Variant variant) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            return null;
        }
        BigDecimal sellingPrice = defaultAmount(variant == null ? null : variant.getSellingPrice());
        if (sellingPrice.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(sellingPrice) >= 0) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal defaultAmount(BigDecimal value) {
        return Objects.requireNonNullElse(value, BigDecimal.ZERO);
    }
}
