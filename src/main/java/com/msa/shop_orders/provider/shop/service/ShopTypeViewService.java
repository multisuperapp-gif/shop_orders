package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.view.ShopTypeView;
import com.msa.shop_orders.provider.shop.view.repository.ShopTypeViewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopTypeViewService {
    private final ShopTypeViewRepository shopTypeViewRepository;
    private final boolean viewStoreEnabled;

    public ShopTypeViewService(
            ShopTypeViewRepository shopTypeViewRepository,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopTypeViewRepository = shopTypeViewRepository;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public boolean isEnabled() {
        return viewStoreEnabled;
    }

    public boolean isActiveType(Long shopTypeId) {
        if (shopTypeId == null) {
            return false;
        }
        if (!viewStoreEnabled) {
            return false;
        }
        ShopTypeView document = shopTypeViewRepository.findById(shopTypeId).orElse(null);
        return document != null && document.isActive();
    }

    public List<ShopTypeView> findActiveTypes() {
        if (!viewStoreEnabled) {
            return List.of();
        }
        return shopTypeViewRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
    }
}
