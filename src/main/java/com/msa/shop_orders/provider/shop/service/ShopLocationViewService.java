package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import com.msa.shop_orders.persistence.repository.ShopLocationRepository;
import com.msa.shop_orders.provider.shop.view.ShopLocationView;
import com.msa.shop_orders.provider.shop.view.repository.ShopLocationViewRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ShopLocationViewService {
    private final ShopLocationViewRepository shopLocationViewRepository;
    private final ShopLocationRepository shopLocationRepository;
    private final boolean viewStoreEnabled;

    public ShopLocationViewService(
            ShopLocationViewRepository shopLocationViewRepository,
            ShopLocationRepository shopLocationRepository,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopLocationViewRepository = shopLocationViewRepository;
        this.shopLocationRepository = shopLocationRepository;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public Optional<Long> findPrimaryLocationId(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        if (viewStoreEnabled) {
            Optional<ShopLocationView> document = shopLocationViewRepository.findFirstByShopIdAndPrimaryTrue(shopId);
            if (document.isPresent()) {
                return Optional.ofNullable(document.get().getId());
            }
        }
        return shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shopId)
                .map(location -> {
                    syncPrimaryLocation(location);
                    return location.getId();
                });
    }

    private void syncPrimaryLocation(ShopLocationEntity location) {
        if (!viewStoreEnabled || location == null || location.getId() == null || location.getShopId() == null) {
            return;
        }
        ShopLocationView document = new ShopLocationView();
        document.setId(location.getId());
        document.setShopId(location.getShopId());
        document.setAddressId(location.getAddressId());
        document.setPrimary(location.isPrimary());
        shopLocationViewRepository.save(document);
    }
}
