package com.msa.shop_orders.common.shoptype;

import java.util.Locale;
import java.util.Map;

public final class RestaurantItemVisibilityPolicy {
    private RestaurantItemVisibilityPolicy() {
    }

    public static boolean isCompatible(String restaurantServiceType, Map<String, Object> attributes) {
        String normalizedType = normalize(restaurantServiceType);
        if (normalizedType.isBlank() || "VEG_NON_VEG".equals(normalizedType)) {
            return true;
        }
        String foodPreference = normalize(readAttribute(attributes, "foodPreference"));
        if (foodPreference.isBlank()) {
            return true;
        }
        if ("PURE_VEG".equals(normalizedType)) {
            return "VEG".equals(foodPreference);
        }
        if ("PURE_NON_VEG".equals(normalizedType) || "NON_VEG_ONLY".equals(normalizedType)) {
            return !"VEG".equals(foodPreference);
        }
        return true;
    }

    private static String readAttribute(Map<String, Object> attributes, String key) {
        if (attributes == null || key == null || key.isBlank()) {
            return null;
        }
        Object value = attributes.get(key);
        return value == null ? null : value.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
