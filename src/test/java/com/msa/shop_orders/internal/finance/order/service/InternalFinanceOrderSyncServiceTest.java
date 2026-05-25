package com.msa.shop_orders.internal.finance.order.service;

import com.msa.shop_orders.consumer.order.service.ShopOrderWriteService;
import com.msa.shop_orders.internal.finance.order.dto.InternalFinanceOrderDtos;
import com.msa.shop_orders.provider.shop.service.ShopInventoryMovementService;
import com.msa.shop_orders.provider.shop.service.ShopOrderStateWriteService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeSyncService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalFinanceOrderSyncServiceTest {

    @Mock
    private ShopOrderWriteService shopOrderWriteService;
    @Mock
    private ShopOrderStateWriteService shopOrderStateWriteService;
    @Mock
    private ShopRuntimeViewService shopRuntimeViewService;
    @Mock
    private ShopRuntimeSyncService shopRuntimeSyncService;
    @Mock
    private ShopInventoryMovementService shopInventoryMovementService;
    @Mock
    private ShopOrderViewRepository shopOrderViewRepository;
    @Mock
    private ShopProductViewRepository shopProductViewRepository;

    private InternalFinanceOrderSyncService service;

    @BeforeEach
    void setUp() {
        service = new InternalFinanceOrderSyncService(
                shopOrderWriteService,
                shopOrderStateWriteService,
                shopRuntimeViewService,
                shopRuntimeSyncService,
                shopInventoryMovementService,
                shopOrderViewRepository,
                shopProductViewRepository
        );
    }

    @Test
    void syncOrderRuntimeRefreshesOrderAndRecordsMovementWhenProvided() {
        service.syncOrderRuntime(41L, new InternalFinanceOrderDtos.RuntimeSyncRequest(
                "CONSUME",
                "Payment completed."
        ));

        verify(shopRuntimeViewService).syncOrderById(41L);
        verify(shopInventoryMovementService).recordOrderMovement(41L, "CONSUME", "Payment completed.", "booking_payment");
    }

    @Test
    void syncOrderRuntimeRefreshesOrderWithoutMovementWhenRequestIsEmpty() {
        service.syncOrderRuntime(41L, new InternalFinanceOrderDtos.RuntimeSyncRequest(null, null));

        verify(shopRuntimeViewService).syncOrderById(41L);
        verify(shopInventoryMovementService, never()).recordOrderMovement(41L, null, null, "booking_payment");
    }
}
