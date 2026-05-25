package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.mongo.MongoSequenceService;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.ShopProductView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopProductWriteServiceTest {

    @Mock
    private ShopProductViewRepository shopProductViewRepository;
    @Mock
    private ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    @Mock
    private MongoSequenceService mongoSequenceService;

    private ShopProductWriteService service;

    @BeforeEach
    void setUp() {
        service = new ShopProductWriteService(
                shopProductViewRepository,
                shopDeliveryRuleViewService,
                mongoSequenceService
        );
    }

    @Test
    void createProductPersistsMongoProductDocumentWithVariantSummary() {
        ShopShellView shop = new ShopShellView();
        shop.setShopId(11L);

        ShopCategoryView category = new ShopCategoryView();
        category.setCategoryId(51L);
        category.setName("Snacks");

        when(mongoSequenceService.nextValue("shop-product-id")).thenReturn(101L);
        when(mongoSequenceService.nextValue("shop-product-variant-id")).thenReturn(201L);
        when(shopDeliveryRuleViewService.findPrimaryDeliveryRule(11L)).thenReturn(Optional.empty());
        when(shopProductViewRepository.save(any(ShopProductView.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductEntity created = service.createProduct(
                shop,
                category,
                new ShopCreateProductRequest(
                        51L,
                        "Masala Chips",
                        "Crispy",
                        "Crispy potato chips",
                        "Crunchy",
                        "STANDARD",
                        false,
                        null,
                        new BigDecimal("150"),
                        "g",
                        new BigDecimal("150"),
                        new BigDecimal("80"),
                        new BigDecimal("70"),
                        12,
                        3,
                        null,
                        "SKU-CHIPS-1",
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        ArgumentCaptor<ShopProductView> captor = ArgumentCaptor.forClass(ShopProductView.class);
        verify(shopProductViewRepository).save(captor.capture());
        ShopProductView saved = captor.getValue();

        assertEquals(101L, created.getId());
        assertEquals(11L, saved.getShopId());
        assertEquals(51L, saved.getCategoryId());
        assertEquals("Snacks", saved.getCategoryName());
        assertEquals("SKU-CHIPS-1", saved.getSku());
        assertEquals("Masala Chips", saved.getItemName());
        assertEquals("IN_STOCK", saved.getInventoryStatus());
        assertNotNull(saved.getVariants());
        assertEquals(1, saved.getVariants().size());
        assertEquals(201L, saved.getVariants().getFirst().getVariantId());
        assertEquals(12, saved.getVariants().getFirst().getQuantityAvailable());
        assertTrue(saved.getVariants().getFirst().isDefaultVariant());
        assertEquals(new BigDecimal("70"), saved.getSellingPrice());
    }
}
