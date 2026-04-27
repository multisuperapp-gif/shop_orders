package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ShopRuntimeSyncService {
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopShellViewService shopShellViewService;
    private final ShopOrderViewRepository shopOrderViewRepository;
    private final boolean viewStoreEnabled;

    public ShopRuntimeSyncService(
            ShopRuntimeViewService shopRuntimeViewService,
            ShopShellViewService shopShellViewService,
            ShopOrderViewRepository shopOrderViewRepository,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopShellViewService = shopShellViewService;
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public void syncProductsForShopAfterCommit(Long shopId) {
        if (shopId == null) {
            return;
        }
        runAfterCommit(() -> shopShellViewService.findByShopId(shopId).ifPresent(shopRuntimeViewService::syncProductsForShop));
    }

    public void syncProductAfterCommit(Long shopId, Long productId) {
        if (shopId == null || productId == null) {
            return;
        }
        runAfterCommit(() -> shopShellViewService.findByShopId(shopId)
                .ifPresent(shellView -> shopRuntimeViewService.syncProductById(shellView, productId)));
    }

    public void syncOrderAfterCommit(Long orderId) {
        if (orderId == null) {
            return;
        }
        runAfterCommit(() -> shopRuntimeViewService.syncOrderById(orderId));
    }

    public void syncOrderAfterCommit(Long orderId, ShopOrderView orderView) {
        if (orderId == null) {
            return;
        }
        if (!viewStoreEnabled || orderView == null || orderView.getOrderId() == null) {
            syncOrderAfterCommit(orderId);
            return;
        }
        runAfterCommit(() -> shopOrderViewRepository.save(orderView));
    }

    public void syncShellAfterCommit(ShopShellView shellView) {
        if (shellView == null || shellView.getShopId() == null) {
            return;
        }
        runAfterCommit(() -> shopShellViewService.syncShellView(shellView));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
