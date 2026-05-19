package com.msa.shop_orders.provider.shop.type.product.restaurant;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.provider.shop.dto.ShopProductImageRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductVariantRequest;
import com.msa.shop_orders.provider.shop.type.product.ProviderShopProductTypeHandler;
import com.msa.shop_orders.provider.shop.type.product.shared.SharedProviderShopProductTypeHandler;
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

@Component
public class RestaurantProviderShopProductTypeHandler implements ProviderShopProductTypeHandler {
    private static final Set<String> ITEM_TYPES = Set.of(
            "PIZZA",
            "BURGER",
            "DRINK",
            "RICE",
            "DAL",
            "PASTA",
            "NOODLES",
            "CURRY",
            "VEGETABLES",
            "BIRYANI",
            "SANDWICH",
            "WRAP",
            "DESSERT",
            "SALAD",
            "SNACKS",
            "THALI",
            "BREADS",
            "COMBO",
            "OTHER"
    );
    private static final Set<String> FOOD_PREFERENCES = Set.of("VEG", "NON_VEG", "EGG");
    private static final Set<String> SPICE_LEVELS = Set.of("MILD", "MEDIUM", "HOT", "EXTRA_HOT");

    private final SharedProviderShopProductTypeHandler delegate;

    public RestaurantProviderShopProductTypeHandler(SharedProviderShopProductTypeHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public ShopTypeFamily family() {
        return ShopTypeFamily.RESTAURANT;
    }

    @Override
    public List<ShopProductData> products(ShopShellView shop, Long categoryId) {
        return delegate.products(shop, categoryId);
    }

    @Override
    public ShopProductData createProduct(ShopShellView shop, ShopCreateProductRequest request) {
        return delegate.createProduct(shop, normalizeRestaurantRequest(shop, request));
    }

    @Override
    public ShopProductData updateProduct(ShopShellView shop, Long productId, ShopCreateProductRequest request) {
        return delegate.updateProduct(shop, productId, normalizeRestaurantRequest(shop, request));
    }

    @Override
    public ShopProductData duplicateProduct(ShopShellView shop, Long productId) {
        return delegate.duplicateProduct(shop, productId);
    }

    @Override
    public ShopProductData updateProductStatus(ShopShellView shop, Long productId, ShopProductStatusUpdateRequest request) {
        return delegate.updateProductStatus(shop, productId, request);
    }

    private ShopCreateProductRequest normalizeRestaurantRequest(ShopShellView shop, ShopCreateProductRequest request) {
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
        List<ShopProductImageRequest> normalizedImages = request.images() == null
                ? null
                : request.images().stream().filter(Objects::nonNull).toList();

        return new ShopCreateProductRequest(
                request.categoryId(),
                normalizeRequiredText(request.itemName(), "ITEM_NAME_REQUIRED", "Item name is required."),
                blankToNull(request.shortDescription()),
                blankToNull(request.description()),
                null,
                restaurantItemType,
                false,
                request.variantName(),
                request.unitValue(),
                request.unitType(),
                null,
                request.mrp(),
                request.sellingPrice(),
                request.quantityAvailable(),
                request.reorderLevel(),
                request.imageFileId(),
                request.sku(),
                request.active(),
                request.featured(),
                normalizedAttributes,
                normalizedVariants,
                normalizedImages,
                request.promotion(),
                request.coupon()
        );
    }

    private Map<String, Object> normalizeRestaurantAttributes(
            String restaurantServiceType,
            Map<String, Object> attributes,
            String productType
    ) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        String restaurantItemType = normalizeOption(
                firstNonBlank(readString(attributes, "restaurantItemType"), productType),
                ITEM_TYPES,
                "RESTAURANT_ITEM_TYPE_REQUIRED",
                "Restaurant item type is required."
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

        Integer prepTimeMinutes = readInteger(attributes, "prepTimeMinutes");
        if (prepTimeMinutes != null) {
            if (prepTimeMinutes < 0) {
                throw new BusinessException("PREP_TIME_INVALID", "Preparation time must be zero or positive.", HttpStatus.BAD_REQUEST);
            }
            normalized.put("prepTimeMinutes", prepTimeMinutes);
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
            throw new BusinessException("PRODUCT_VARIANT_REQUIRED", "At least one restaurant size or portion option is required.", HttpStatus.BAD_REQUEST);
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
        if (normalized.isEmpty()) {
            throw new BusinessException("PRODUCT_VARIANT_REQUIRED", "At least one restaurant size or portion option is required.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
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

    private String readString(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        return value == null ? null : value.toString();
    }

    private Integer readInteger(Map<String, Object> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String normalized = value.toString().trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            throw new BusinessException("ATTRIBUTE_INVALID", key + " must be a valid integer.", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeOption(String value, Set<String> allowed, String code, String message) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        if (!allowed.contains(normalized)) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeOptionalOption(String value, Set<String> allowed, String code) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("NOT_APPLICABLE".equals(normalized)) {
            return null;
        }
        if (!allowed.contains(normalized)) {
            throw new BusinessException(code, "Restaurant spice level is invalid.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeDisplayText(String value) {
        String cleaned = normalizeRequiredText(value, "TEXT_REQUIRED", "Text is required.");
        String[] parts = cleaned.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String normalizeOptionalDisplayText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeDisplayText(value);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String firstNonBlank(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return secondary;
    }
}
