package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ShopRuntimeSyncService {
    private final ShopShellViewService shopShellViewService;
    private final ShopOrderViewRepository shopOrderViewRepository;

    public ShopRuntimeSyncService(
            ShopShellViewService shopShellViewService,
            ShopOrderViewRepository shopOrderViewRepository
    ) {
        this.shopShellViewService = shopShellViewService;
        this.shopOrderViewRepository = shopOrderViewRepository;
    }

    public void syncProductsForShopAfterCommit(Long shopId) {
        // Shop runtime source of truth is Mongo; there is no SQL rebuild step here anymore.
    }

    public void syncProductAfterCommit(Long shopId, Long productId) {
        // Shop runtime source of truth is Mongo; there is no SQL rebuild step here anymore.
    }

    public void syncOrderAfterCommit(Long orderId) {
        // Shop runtime source of truth is Mongo; there is no SQL rebuild step here anymore.
    }

    public void syncOrderAfterCommit(Long orderId, ShopOrderView orderView) {
        if (orderId == null) {
            return;
        }
        if (orderView == null || orderView.getOrderId() == null) {
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
