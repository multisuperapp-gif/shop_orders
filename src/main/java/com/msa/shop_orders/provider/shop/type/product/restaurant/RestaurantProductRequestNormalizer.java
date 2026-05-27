package com.msa.shop_orders.provider.shop.type.product.restaurant;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductImageRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductPromotionRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductVariantRequest;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.math.BigDecimal;

@Component
public class RestaurantProductRequestNormalizer {
    private static final Set<String> FOOD_PREFERENCES = Set.of("VEG", "NON_VEG", "EGG");
    private static final Set<String> SPICE_LEVELS = Set.of("MILD", "MEDIUM", "HOT", "EXTRA_HOT");

    public ShopCreateProductRequest normalize(ShopShellView shop, ShopCreateProductRequest request) {
        if (request == null) {
            throw new BusinessException("PRODUCT_REQUIRED", "Product payload is required.", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> normalizedAttributes = normalizeRestaurantAttributes(
                shop == null ? null : shop.getRestaurantServiceType(),
                request.attributes(),
                request.productType()
        );
        String restaurantItemType = normalizedAttributes.get("restaurantItemType").toString();
        List<ShopProductVariantRequest> normalizedVariants = normalizeVariants(request.variants());
        ensureStandalonePricingPresent(request, normalizedVariants);
        List<ShopProductImageRequest> normalizedImages = request.images() == null
                ? null
                : request.images().stream().filter(Objects::nonNull).toList();
        ShopProductPromotionRequest normalizedPromotion = normalizePromotion(request.promotion(), request.sellingPrice(), normalizedVariants);

        return new ShopCreateProductRequest(
                request.categoryId(),
                normalizeRequiredText(request.itemName(), "ITEM_NAME_REQUIRED", "Item name is required."),
                blankToNull(request.shortDescription()),
                blankToNull(request.description()),
                null,
                restaurantItemType,
                false,
                blankToNull(request.variantName()),
                request.unitValue(),
                request.unitType(),
                null,
                request.mrp(),
                request.sellingPrice(),
                request.quantityAvailable(),
                request.reorderLevel(),
                request.imageFileId(),
                null,
                request.active(),
                request.featured(),
                normalizedAttributes,
                normalizedVariants,
                normalizedImages,
                normalizedPromotion,
                null
        );
    }

    private ShopProductPromotionRequest normalizePromotion(
            ShopProductPromotionRequest promotion,
            BigDecimal sellingPrice,
            List<ShopProductVariantRequest> normalizedVariants
    ) {
        if (promotion == null) {
            return null;
        }
        if (Boolean.FALSE.equals(promotion.enabled())) {
            return null;
        }
        boolean hasPayload = Boolean.TRUE.equals(promotion.enabled())
                || promotion.startsAt() != null
                || promotion.endsAt() != null
                || promotion.paidAmount() != null;
        if (!hasPayload) {
            return null;
        }
        boolean hasMultipleVariants = normalizedVariants != null && normalizedVariants.size() > 1;
        if (!hasMultipleVariants) {
            BigDecimal dealPrice = promotion.paidAmount();
            if (dealPrice != null
                    && sellingPrice != null
                    && dealPrice.compareTo(sellingPrice) >= 0) {
                throw new BusinessException(
                        "PROMOTION_PRICE_INVALID",
                        "Deal price must be lower than the current selling price.",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
        return new ShopProductPromotionRequest(
                promotion.enabled(),
                "DEAL",
                promotion.startsAt(),
                promotion.endsAt(),
                0,
                promotion.paidAmount(),
                "ACTIVE"
        );
    }

    private Map<String, Object> normalizeRestaurantAttributes(
            String restaurantServiceType,
            Map<String, Object> attributes,
            String productType
    ) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        String restaurantItemType = normalizeRestaurantItemType(
                firstNonBlank(readString(attributes, "restaurantItemType"), productType)
        );
        normalized.put("shopTypeFamily", "RESTAURANT");
        normalized.put("restaurantItemType", restaurantItemType);

        String foodPreference = normalizeOption(
                readString(attributes, "foodPreference"),
                FOOD_PREFERENCES,
                "FOOD_PREFERENCE_REQUIRED",
                "Food preference is required for restaurant items."
        );
        validateFoodPreferenceAgainstRestaurantType(restaurantServiceType, foodPreference);
        normalized.put("foodPreference", foodPreference);

        String spiceLevel = normalizeOptionalOption(readString(attributes, "spiceLevel"), SPICE_LEVELS, "SPICE_LEVEL_INVALID");
        if (spiceLevel != null) {
            normalized.put("spiceLevel", spiceLevel);
        }

        Integer maxOrderQuantity = readInteger(attributes, "maxOrderQuantity");
        if (maxOrderQuantity != null) {
            if (maxOrderQuantity <= 0) {
                throw new BusinessException("MAX_ORDER_QUANTITY_INVALID", "Maximum quantity per order must be greater than zero.", HttpStatus.BAD_REQUEST);
            }
            normalized.put("maxOrderQuantity", maxOrderQuantity);
        }

        List<String> addonOptions = normalizeAddonOptions(attributes == null ? null : attributes.get("addonOptions"));
        if (!addonOptions.isEmpty()) {
            normalized.put("addonOptions", addonOptions);
        }
        return normalized;
    }

    private void validateFoodPreferenceAgainstRestaurantType(String restaurantServiceType, String foodPreference) {
        String normalizedType = restaurantServiceType == null ? "" : restaurantServiceType.trim().toUpperCase(Locale.ROOT);
        if (normalizedType.isBlank() || foodPreference == null || foodPreference.isBlank()) {
            return;
        }
        if ("PURE_VEG".equals(normalizedType) && !"VEG".equals(foodPreference)) {
            throw new BusinessException(
                    "RESTAURANT_ITEM_NOT_ALLOWED",
                    "Pure Veg restaurants can only add Veg items.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if ("PURE_NON_VEG".equals(normalizedType) && "VEG".equals(foodPreference)) {
            throw new BusinessException(
                    "RESTAURANT_ITEM_NOT_ALLOWED",
                    "Pure NonVeg restaurants cannot add Veg items.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private List<ShopProductVariantRequest> normalizeVariants(List<ShopProductVariantRequest> variants) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        List<ShopProductVariantRequest> normalized = new ArrayList<>();
        for (ShopProductVariantRequest variant : variants) {
            if (variant == null) {
                continue;
            }
            String label = normalizeRequiredText(
                    variant.variantName(),
                    "VARIANT_NAME_REQUIRED",
                    "Restaurant options need a size or portion label."
            );
            LinkedHashMap<String, Object> variantAttributes = new LinkedHashMap<>();
            if (variant.attributes() != null) {
            variantAttributes.putAll(variant.attributes());
        }
        variantAttributes.put("serviceMode", "RESTAURANT");
        validateVariantPromotion(variant, variantAttributes);
        normalized.add(new ShopProductVariantRequest(
                    variant.variantId(),
                    variant.clientKey(),
                    normalizeDisplayText(label),
                    null,
                    null,
                    variant.unitValue(),
                    blankToNull(variant.unitType()),
                    null,
                    variant.mrp(),
                    variant.sellingPrice(),
                    variant.quantityAvailable(),
                    variant.reorderLevel(),
                    variant.defaultVariant(),
                    variant.active(),
                    variant.sortOrder(),
                    variantAttributes
            ));
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateVariantPromotion(
            ShopProductVariantRequest variant,
            Map<String, Object> variantAttributes
    ) {
        if (!readBoolean(variantAttributes, "promotionEnabled")) {
            return;
        }
        BigDecimal dealPrice = readBigDecimal(variantAttributes, "promotionPaidAmount");
        if (dealPrice == null) {
            throw new BusinessException(
                    "VARIANT_PROMOTION_PRICE_REQUIRED",
                    "Each promoted size needs a valid deal price.",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (variant.sellingPrice() != null && dealPrice.compareTo(variant.sellingPrice()) >= 0) {
            throw new BusinessException(
                    "VARIANT_PROMOTION_PRICE_INVALID",
                    "Deal price must be lower than the current selling price for each promoted size.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private List<String> normalizeAddonOptions(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (rawValue instanceof List<?> rawList) {
            for (Object rawItem : rawList) {
                String normalized = normalizeOptionalDisplayText(rawItem == null ? null : rawItem.toString());
                if (normalized != null) {
                    values.add(normalized);
                }
            }
        } else {
            String rawText = rawValue.toString();
            for (String part : rawText.split(",")) {
                String normalized = normalizeOptionalDisplayText(part);
                if (normalized != null) {
                    values.add(normalized);
                }
            }
        }
        return List.copyOf(values);
    }

    private void ensureStandalonePricingPresent(
            ShopCreateProductRequest request,
            List<ShopProductVariantRequest> normalizedVariants
    ) {
        if (normalizedVariants != null && !normalizedVariants.isEmpty()) {
            return;
        }
        if (request.mrp() == null) {
            throw new BusinessException("MRP_REQUIRED", "Base price is required for restaurant items.", HttpStatus.BAD_REQUEST);
        }
        if (request.sellingPrice() == null) {
            throw new BusinessException("SELLING_PRICE_REQUIRED", "Selling price is required for restaurant items.", HttpStatus.BAD_REQUEST);
        }
        if (request.quantityAvailable() == null) {
            throw new BusinessException("QUANTITY_REQUIRED", "Available quantity is required for restaurant items.", HttpStatus.BAD_REQUEST);
        }
    }

    private String readString(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        return value == null ? null : value.toString();
    }

    private Integer readInteger(Map<String, Object> attributes, String key) {
        String raw = readString(attributes, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(key.toUpperCase(Locale.ROOT) + "_INVALID", "Invalid value for " + key + ".", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean readBoolean(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return false;
        }
        Object value = attributes.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            return "true".equalsIgnoreCase(stringValue.trim());
        }
        return false;
    }

    private BigDecimal readBigDecimal(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimalValue) {
            return decimalValue;
        }
        if (value instanceof Number numberValue) {
            return BigDecimal.valueOf(numberValue.doubleValue());
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException exception) {
                throw new BusinessException(
                        key.toUpperCase(Locale.ROOT) + "_INVALID",
                        "Invalid value for " + key + ".",
                        HttpStatus.BAD_REQUEST
                );
            }
        }
        return null;
    }

    private String normalizeOption(String value, Set<String> allowed, String code, String message) {
        String normalized = normalizeRequiredText(value, code, message).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!allowed.contains(normalized)) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalOption(String value, Set<String> allowed, String code) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!allowed.contains(normalized)) {
            throw new BusinessException(code, "Invalid value supplied.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeRestaurantItemType(String value) {
        String normalized = normalizeRequiredText(
                value,
                "RESTAURANT_ITEM_TYPE_REQUIRED",
                "Restaurant item type is required."
        );
        return normalized
                .trim()
                .replaceAll("\\s+", "_")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeRequiredText(String value, String code, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeDisplayText(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? "" : normalized.replaceAll("\\s+", " ");
    }

    private String normalizeOptionalDisplayText(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.replaceAll("\\s+", " ");
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = blankToNull(first);
        if (normalizedFirst != null) {
            return normalizedFirst;
        }
        return blankToNull(second);
    }
}
