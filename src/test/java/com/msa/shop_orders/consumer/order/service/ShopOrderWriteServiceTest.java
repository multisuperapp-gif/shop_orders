package com.msa.shop_orders.consumer.order.service;

import com.msa.shop_orders.common.mongo.MongoSequenceService;
import com.msa.shop_orders.common.settings.ShopFeeSettingsService;
import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import com.msa.shop_orders.persistence.entity.UserAddressEntity;
import com.msa.shop_orders.persistence.repository.ShopLocationRepository;
import com.msa.shop_orders.persistence.repository.UserAddressRepository;
import com.msa.shop_orders.provider.shop.dto.ShopProductDeliveryRuleData;
import com.msa.shop_orders.provider.shop.service.ShopDeliveryRuleViewService;
import com.msa.shop_orders.provider.shop.service.ShopShellViewService;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopProductView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopOrderWriteServiceTest {

    @Mock
    private ShopLocationRepository shopLocationRepository;
    @Mock
    private ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    @Mock
    private UserAddressRepository userAddressRepository;
    @Mock
    private ShopShellViewRepository shopShellViewRepository;
    @Mock
    private ShopShellViewService shopShellViewService;
    @Mock
    private ShopProductViewRepository shopProductViewRepository;
    @Mock
    private ShopOrderViewRepository shopOrderViewRepository;
    @Mock
    private MongoSequenceService mongoSequenceService;
    @Mock
    private ShopFeeSettingsService shopFeeSettingsService;

    private ShopOrderWriteService service;

    @BeforeEach
    void setUp() {
        service = new ShopOrderWriteService(
                shopLocationRepository,
                shopDeliveryRuleViewService,
                userAddressRepository,
                shopShellViewRepository,
                shopShellViewService,
                shopProductViewRepository,
                shopOrderViewRepository,
                mongoSequenceService,
                shopFeeSettingsService
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void createOrderPersistsMongoOrderAndReservesVariantStock() {
        UserAddressEntity address = new UserAddressEntity();
        address.setId(401L);
        address.setUserId(7L);
        address.setAddressScope("CONSUMER");

        ShopProductView.Variant variant = new ShopProductView.Variant();
        variant.setVariantId(201L);
        variant.setVariantName("Regular");
        variant.setSellingPrice(new BigDecimal("120"));
        variant.setQuantityAvailable(5);
        variant.setReservedQuantity(1);
        variant.setReorderLevel(1);
        variant.setInventoryStatus("IN_STOCK");
        variant.setDefaultVariant(true);
        variant.setActive(true);

        ShopProductView product = new ShopProductView();
        product.setProductId(101L);
        product.setShopId(11L);
        product.setItemName("Veg Burger");
        product.setImageFileId(901L);
        product.setActive(true);
        product.setVariants(List.of(variant));

        ShopLocationEntity location = new ShopLocationEntity();
        location.setId(301L);
        location.setShopId(11L);
        location.setPrimary(true);

        ShopShellView shop = new ShopShellView();
        shop.setShopId(11L);
        shop.setShopName("Burger Point");

        when(userAddressRepository.findById(401L)).thenReturn(Optional.of(address));
        when(shopProductViewRepository.findByVariantsVariantIdIn(any())).thenReturn(List.of(product));
        when(shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(11L)).thenReturn(Optional.of(location));
        when(shopDeliveryRuleViewService.findPrimaryDeliveryRule(11L)).thenReturn(Optional.of(
                new ShopProductDeliveryRuleData(301L, "DELIVERY", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20"), null, 30, 60)
        ));
        when(shopShellViewService.findByShopId(11L)).thenReturn(Optional.of(shop));
        // Platform fee is now resolved from app_settings (platform.fee.shop) on the subtotal.
        when(shopFeeSettingsService.shopPlatformFeeAmount(any())).thenReturn(new BigDecimal("5.00"));
        when(mongoSequenceService.nextValue("shop-order-id")).thenReturn(501L);
        when(shopOrderViewRepository.save(any(ShopOrderView.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShopOrderWriteService.CreatedOrder created = service.createOrder(
                new ShopOrderWriteService.CreateOrderCommand(
                        7L,
                        401L,
                        "DELIVERY",
                        new BigDecimal("5"),
                        "INR",
                        "PAYMENT_PENDING",
                        "UNPAID",
                        "Order created",
                        LocalDateTime.of(2026, 5, 25, 10, 0),
                        "Home",
                        "Street 1",
                        "PAY-1",
                        List.of(new ShopOrderWriteService.CreateOrderItemCommand(
                                201L,
                                2,
                                null,
                                null,
                                null,
                                null
                        ))
                )
        );

        assertEquals(501L, created.orderId());
        assertEquals(11L, created.shopId());
        assertEquals(new BigDecimal("240"), created.subtotalAmount());
        assertEquals(new BigDecimal("20"), created.deliveryFeeAmount());
        assertEquals(new BigDecimal("265.00"), created.totalAmount());

        ArgumentCaptor<ShopOrderView> orderCaptor = ArgumentCaptor.forClass(ShopOrderView.class);
        verify(shopOrderViewRepository).save(orderCaptor.capture());
        ShopOrderView savedOrder = orderCaptor.getValue();
        assertEquals(501L, savedOrder.getOrderId());
        assertEquals("Burger Point", savedOrder.getShopName());
        assertEquals("PAYMENT_PENDING", savedOrder.getOrderStatus());
        assertEquals("UNPAID", savedOrder.getPaymentStatus());
        assertEquals(1, savedOrder.getItems().size());
        assertEquals("Veg Burger (Regular)", savedOrder.getItems().getFirst().getItemName());
        assertNotNull(savedOrder.getTimeline());
        assertEquals(1, savedOrder.getTimeline().size());

        ArgumentCaptor<Iterable<ShopProductView>> productsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(shopProductViewRepository).saveAll(productsCaptor.capture());
        ShopProductView savedProduct = productsCaptor.getValue().iterator().next();
        assertEquals(3, savedProduct.getVariants().getFirst().getReservedQuantity());
        assertEquals("IN_STOCK", savedProduct.getVariants().getFirst().getInventoryStatus());
    }
}
