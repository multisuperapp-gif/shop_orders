package com.msa.shop_orders.consumer.storefront.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.shop_orders.consumer.storefront.dto.StorefrontDtos;
import com.msa.shop_orders.persistence.repository.StorefrontCatalogRepository;
import com.msa.shop_orders.provider.shop.service.ShopCategoryViewService;
import com.msa.shop_orders.provider.shop.service.ShopDeliveryRuleViewService;
import com.msa.shop_orders.provider.shop.service.ShopOperatingHoursViewService;
import com.msa.shop_orders.provider.shop.service.ShopShellViewService;
import com.msa.shop_orders.provider.shop.view.ShopProductView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorefrontCatalogServiceTest {

    @Mock
    private StorefrontCatalogRepository storefrontCatalogRepository;
    @Mock
    private ShopCategoryViewService shopCategoryViewService;
    @Mock
    private ShopProductViewRepository shopProductViewRepository;
    @Mock
    private ShopShellViewRepository shopShellViewRepository;
    @Mock
    private ShopShellViewService shopShellViewService;
    @Mock
    private ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    @Mock
    private ShopOperatingHoursViewService shopOperatingHoursViewService;
    @Mock
    private ObjectMapper objectMapper;

    private StorefrontCatalogService service;

    @BeforeEach
    void setUp() {
        service = new StorefrontCatalogService(
                storefrontCatalogRepository,
                shopCategoryViewService,
                shopProductViewRepository,
                shopShellViewRepository,
                shopShellViewService,
                shopDeliveryRuleViewService,
                shopOperatingHoursViewService,
                objectMapper
        );
    }

    @Test
    void findProductsUsesMongoProductsForApprovedShop() {
        ShopShellView shop = approvedShop(11L, 7L, "Fresh Mart");

        ShopProductView.Variant variant = new ShopProductView.Variant();
        variant.setVariantId(201L);
        variant.setVariantName("1 kg");
        variant.setMrp(new BigDecimal("120"));
        variant.setSellingPrice(new BigDecimal("99"));
        variant.setQuantityAvailable(10);
        variant.setReservedQuantity(1);
        variant.setInventoryStatus("IN_STOCK");
        variant.setDefaultVariant(true);
        variant.setActive(true);
        variant.setSortOrder(0);

        ShopProductView product = new ShopProductView();
        product.setProductId(101L);
        product.setShopId(11L);
        product.setCategoryId(5L);
        product.setCategoryName("Fruits");
        product.setItemName("Apple");
        product.setShortDescription("Fresh apple");
        product.setBrandName("Local Farm");
        product.setProductType("SHOP");
        product.setAvgRating(new BigDecimal("4.7"));
        product.setTotalReviews(12);
        product.setTotalOrders(45);
        product.setInventoryStatus("IN_STOCK");
        product.setActive(true);
        product.setFeatured(true);
        product.setVariants(List.of(variant));

        when(shopShellViewRepository.findByApprovalStatusAndShopTypeId("APPROVED", 7L)).thenReturn(List.of(shop));
        when(shopShellViewService.findByShopId(11L)).thenReturn(Optional.of(shop));
        when(shopProductViewRepository.findAll()).thenReturn(List.of(product));

        StorefrontDtos.PageResponse<StorefrontDtos.ShopProductCardData> response =
                service.findProducts(7L, 5L, "apple", null, null, 0, 20);

        assertEquals(1, response.items().size());
        StorefrontDtos.ShopProductCardData card = response.items().getFirst();
        assertEquals(101L, card.productId());
        assertEquals(201L, card.variantId());
        assertEquals("Fresh Mart", card.shopName());
        assertEquals(new BigDecimal("99"), card.sellingPrice());
        assertFalse(card.outOfStock());
    }

    @Test
    void findProductDetailUsesMongoProductDocument() {
        ShopShellView shop = approvedShop(11L, 7L, "Fresh Mart");

        ShopProductView.Variant variant = new ShopProductView.Variant();
        variant.setVariantId(201L);
        variant.setVariantName("1 kg");
        variant.setMrp(new BigDecimal("120"));
        variant.setSellingPrice(new BigDecimal("99"));
        variant.setQuantityAvailable(10);
        variant.setReservedQuantity(1);
        variant.setInventoryStatus("IN_STOCK");
        variant.setDefaultVariant(true);
        variant.setActive(true);
        variant.setSortOrder(0);

        ShopProductView.Image image = new ShopProductView.Image();
        image.setImageId(301L);
        image.setImageRole("PRIMARY");
        image.setSortOrder(0);
        image.setPrimaryImage(true);

        ShopProductView product = new ShopProductView();
        product.setProductId(101L);
        product.setShopId(11L);
        product.setCategoryId(5L);
        product.setCategoryName("Fruits");
        product.setItemName("Apple");
        product.setShortDescription("Fresh apple");
        product.setDescription("Fresh and crispy");
        product.setBrandName("Local Farm");
        product.setProductType("SHOP");
        product.setAvgRating(new BigDecimal("4.7"));
        product.setTotalReviews(12);
        product.setTotalOrders(45);
        product.setInventoryStatus("IN_STOCK");
        product.setActive(true);
        product.setVariants(List.of(variant));
        product.setImages(List.of(image));

        when(shopProductViewRepository.findById(101L)).thenReturn(Optional.of(product));
        when(shopShellViewRepository.findById(11L)).thenReturn(Optional.of(shop));

        StorefrontDtos.ProductDetailData detail = service.findProductDetail(101L, null);

        assertEquals(101L, detail.productId());
        assertEquals(201L, detail.selectedVariantId());
        assertEquals("Fresh Mart", detail.shopName());
        assertEquals(1, detail.variants().size());
        assertEquals(1, detail.images().size());
        assertNull(detail.images().getFirst().objectKey());
        assertEquals(0, detail.optionGroups().size());
    }

    private ShopShellView approvedShop(Long shopId, Long shopTypeId, String shopName) {
        ShopShellView shop = new ShopShellView();
        shop.setShopId(shopId);
        shop.setShopTypeId(shopTypeId);
        shop.setShopName(shopName);
        shop.setApprovalStatus("APPROVED");
        shop.setOperationalStatus("ACTIVE");
        return shop;
    }
}
