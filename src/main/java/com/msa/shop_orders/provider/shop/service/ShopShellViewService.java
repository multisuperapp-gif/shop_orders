package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ShopShellViewService {
    private final ShopShellViewRepository shopShellViewRepository;
    private final boolean viewStoreEnabled;

    public ShopShellViewService(
            ShopShellViewRepository shopShellViewRepository,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopShellViewRepository = shopShellViewRepository;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public Optional<ShopShellView> findCurrentApprovedShop(Long ownerUserId) {
        if (!viewStoreEnabled || ownerUserId == null) {
            return Optional.empty();
        }
        return shopShellViewRepository.findFirstByOwnerUserId(ownerUserId);
    }

    public Optional<ShopShellView> findByShopId(Long shopId) {
        if (!viewStoreEnabled || shopId == null) {
            return Optional.empty();
        }
        return shopShellViewRepository.findById(shopId);
    }

    public void syncShellView(ShopShellView document) {
        if (!viewStoreEnabled || document == null || document.getShopId() == null) {
            return;
        }
        shopShellViewRepository.save(document);
    }
}
