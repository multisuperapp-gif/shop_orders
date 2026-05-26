package com.msa.shop_orders.provider.shop.type.product.restaurant;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductVariantRequest;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestaurantProductRequestNormalizerTest {

    private final RestaurantProductRequestNormalizer normalizer = new RestaurantProductRequestNormalizer();

    @Test
    void normalizeDoesNotRequireSkuOrGenericVariantsForRestaurantItems() {
        ShopShellView shop = new ShopShellView();
        shop.setRestaurantServiceType("PURE_VEG");

        ShopCreateProductRequest normalized = normalizer.normalize(shop, new ShopCreateProductRequest(
                9L,
                "Paneer Tikka",
                "Fresh starter",
                "Clay oven paneer",
                null,
                "starter",
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("240"),
                new BigDecimal("199"),
                30,
                5,
                null,
                "REST-KEEP-OUT",
                true,
                true,
                Map.of(
                        "foodPreference", "veg",
                        "prepTimeMinutes", "15"
                ),
                null,
                null,
                null,
                null
        ));

        assertNull(normalized.sku());
        assertNull(normalized.variants());
        assertEquals("STARTER", normalized.productType());
        assertEquals("VEG", normalized.attributes().get("foodPreference"));
        assertEquals(30, normalized.quantityAvailable());
    }

    @Test
    void normalizeStillAcceptsRestaurantPortionRowsWhenProvided() {
        ShopCreateProductRequest normalized = normalizer.normalize(null, new ShopCreateProductRequest(
                9L,
                "Veg Biryani",
                null,
                null,
                null,
                "rice bowl",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                false,
                Map.of("foodPreference", "veg"),
                List.of(new ShopProductVariantRequest(
                        null,
                        "regular",
                        "Regular",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("180"),
                        new BigDecimal("160"),
                        12,
                        3,
                        true,
                        true,
                        0,
                        null
                )),
                null,
                null,
                null
        ));

        assertEquals(1, normalized.variants().size());
        assertEquals("Regular", normalized.variants().getFirst().variantName());
        assertNull(normalized.sku());
    }

    @Test
    void normalizeRequiresBasePricingWhenRestaurantPortionsAreMissing() {
        BusinessException exception = assertThrows(BusinessException.class, () -> normalizer.normalize(
                null,
                new ShopCreateProductRequest(
                        9L,
                        "Momos",
                        null,
                        null,
                        null,
                        "snacks",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("120"),
                        10,
                        2,
                        null,
                        null,
                        true,
                        false,
                        Map.of("foodPreference", "veg"),
                        null,
                        null,
                        null,
                        null
                )
        ));

        assertEquals("MRP_REQUIRED", exception.getErrorCode());
    }
}
