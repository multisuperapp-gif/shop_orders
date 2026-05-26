package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.mongo.MongoSequenceService;
import com.msa.shop_orders.provider.shop.dto.ShopCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCreateCategoryRequest;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopCategoryServiceImplTest {

    @Mock
    private ShopContextService shopContextService;
    @Mock
    private ShopTypeViewService shopTypeViewService;
    @Mock
    private ShopCategoryViewService shopCategoryViewService;
    @Mock
    private MongoSequenceService mongoSequenceService;

    private ShopCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShopCategoryServiceImpl(
                shopContextService,
                shopTypeViewService,
                shopCategoryViewService,
                mongoSequenceService
        );
    }

    @Test
    void createCategoryCreatesMongoBackedCategoryAndAddsItToShop() {
        ShopShellView shop = new ShopShellView();
        shop.setShopId(11L);
        shop.setShopTypeId(7L);

        when(shopContextService.currentApprovedShop()).thenReturn(shop);
        when(shopTypeViewService.isActiveType(7L)).thenReturn(true);
        when(shopCategoryViewService.findTypeCategoryByNormalizedName("FRESH VEG")).thenReturn(Optional.empty());
        when(shopCategoryViewService.findShopCategory(11L, 7L, 101L)).thenReturn(Optional.empty());
        when(shopCategoryViewService.findShopCategories(11L, 7L)).thenReturn(List.of());
        when(mongoSequenceService.nextValue("shop-category-id")).thenReturn(101L);

        ShopCategoryData created = service.createCategory(new ShopCreateCategoryRequest("fresh   veg"));

        assertEquals(101L, created.id());
        assertEquals("Fresh Veg", created.name());
        assertEquals(true, created.enabled());
        assertEquals(0, created.sortOrder());
        verify(shopCategoryViewService).upsertTypeCategory(7L, 101L, "Fresh Veg", "FRESH VEG", true);
        verify(shopCategoryViewService).upsertShopCategory(11L, 7L, 101L, "Fresh Veg", "FRESH VEG", true, 0);
    }

    @Test
    void updateCategoryStatusTogglesExistingShopCategory() {
        ShopShellView shop = new ShopShellView();
        shop.setShopId(11L);
        shop.setShopTypeId(7L);

        ShopCategoryView existing = new ShopCategoryView();
        existing.setCategoryId(55L);
        existing.setName("Dairy");
        existing.setNormalizedName("DAIRY");
        existing.setSortOrder(3);

        when(shopContextService.currentApprovedShop()).thenReturn(shop);
        when(shopTypeViewService.isActiveType(7L)).thenReturn(true);
        when(shopCategoryViewService.findShopCategory(11L, 7L, 55L)).thenReturn(Optional.of(existing));

        ShopCategoryData updated = service.updateCategoryStatus(55L, new com.msa.shop_orders.provider.shop.dto.ShopCategoryStatusUpdateRequest(false));

        assertEquals(55L, updated.id());
        assertEquals("Dairy", updated.name());
        assertEquals(false, updated.enabled());
        assertEquals(3, updated.sortOrder());
        verify(shopCategoryViewService).upsertShopCategory(11L, 7L, 55L, "Dairy", "DAIRY", false, 3);
    }
}
