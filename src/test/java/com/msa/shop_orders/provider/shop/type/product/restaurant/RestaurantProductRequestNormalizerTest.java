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

    @Test
    void normalizeRejectsDealPriceEqualToSellingPriceForSingleSizeItem() {
        BusinessException exception = assertThrows(BusinessException.class, () -> normalizer.normalize(
                null,
                new ShopCreateProductRequest(
                        9L,
                        "Cold Coffee",
                        null,
                        null,
                        null,
                        "beverage",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("120"),
                        new BigDecimal("99"),
                        10,
                        2,
                        null,
                        null,
                        true,
                        false,
                        Map.of("foodPreference", "veg"),
                        null,
                        null,
                        new com.msa.shop_orders.provider.shop.dto.ShopProductPromotionRequest(
                                true,
                                "DEAL",
                                java.time.LocalDateTime.now().plusDays(1),
                                java.time.LocalDateTime.now().plusDays(2),
                                0,
                                new BigDecimal("99"),
                                "ACTIVE"
                        ),
                        null
                )
        ));

        assertEquals("PROMOTION_PRICE_INVALID", exception.getErrorCode());
    }

    @Test
    void normalizeRejectsVariantDealPriceEqualToSellingPrice() {
        BusinessException exception = assertThrows(BusinessException.class, () -> normalizer.normalize(
                null,
                new ShopCreateProductRequest(
                        9L,
                        "Orange Juice",
                        null,
                        null,
                        null,
                        "drink",
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
                        List.of(
                                new ShopProductVariantRequest(
                                        null,
                                        "small",
                                        "250 ml",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        new BigDecimal("60"),
                                        new BigDecimal("50"),
                                        20,
                                        5,
                                        true,
                                        true,
                                        0,
                                        Map.of(
                                                "promotionEnabled", true,
                                                "promotionPaidAmount", new BigDecimal("50"),
                                                "promotionStartsAt", "2026-05-28T00:00:00",
                                                "promotionEndsAt", "2026-05-29T23:59:59"
                                        )
                                ),
                                new ShopProductVariantRequest(
                                        null,
                                        "large",
                                        "500 ml",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        new BigDecimal("100"),
                                        new BigDecimal("90"),
                                        20,
                                        5,
                                        false,
                                        true,
                                        1,
                                        null
                                )
                        ),
                        null,
                        null,
                        null
                )
        ));

        assertEquals("VARIANT_PROMOTION_PRICE_INVALID", exception.getErrorCode());
    }
}
