package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopOrderStateWriteServiceTest {

    @Mock
    private ShopOrderViewRepository shopOrderViewRepository;

    private ShopOrderStateWriteService service;

    @BeforeEach
    void setUp() {
        service = new ShopOrderStateWriteService(shopOrderViewRepository);
    }

    @Test
    void applyStateUpdateUpdatesMongoOrderAndAppendsTimeline() {
        ShopOrderView order = new ShopOrderView();
        order.setOrderId(41L);
        order.setOrderStatus("PREPARING");
        order.setPaymentStatus("UNPAID");

        when(shopOrderViewRepository.findById(41L)).thenReturn(Optional.of(order));
        when(shopOrderViewRepository.save(any(ShopOrderView.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShopOrderView updated = service.applyStateUpdate(
                41L,
                new ShopOrderStateWriteService.OrderStateMutation(
                        "DISPATCHED",
                        "paid",
                        9L,
                        "Packed and handed over",
                        null
                )
        );

        assertSame(order, updated);
        assertEquals("DISPATCHED", updated.getOrderStatus());
        assertEquals("PAID", updated.getPaymentStatus());
        assertNotNull(updated.getUpdatedAt());
        assertNotNull(updated.getTimeline());
        assertEquals(1, updated.getTimeline().size());
        assertEquals("PREPARING", updated.getTimeline().getFirst().getOldStatus());
        assertEquals("DISPATCHED", updated.getTimeline().getFirst().getNewStatus());
        assertEquals("Packed and handed over", updated.getTimeline().getFirst().getReason());
        assertNotNull(updated.getTimeline().getFirst().getChangedAt());
        verify(shopOrderViewRepository).save(order);
    }

    @Test
    void applyStateUpdateReturnsNullWhenNothingChanges() {
        ShopOrderView order = new ShopOrderView();
        order.setOrderId(41L);
        order.setOrderStatus("PREPARING");
        order.setPaymentStatus("PAID");

        when(shopOrderViewRepository.findById(41L)).thenReturn(Optional.of(order));

        ShopOrderView updated = service.applyStateUpdate(
                41L,
                new ShopOrderStateWriteService.OrderStateMutation(
                        "PREPARING",
                        "   ",
                        9L,
                        "No-op",
                        null
                )
        );

        assertNull(updated);
        verify(shopOrderViewRepository, never()).save(any(ShopOrderView.class));
    }
}
