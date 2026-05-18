package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.ShopTypeEntity;
import com.msa.shop_orders.persistence.repository.ShopTypeRepository;
import com.msa.shop_orders.provider.shop.view.ShopTypeView;
import com.msa.shop_orders.provider.shop.view.repository.ShopTypeViewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopTypeViewService {
    private final ShopTypeViewRepository shopTypeViewRepository;
    private final ShopTypeRepository shopTypeRepository;
    private final boolean viewStoreEnabled;

    public ShopTypeViewService(
            ShopTypeViewRepository shopTypeViewRepository,
            ShopTypeRepository shopTypeRepository,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopTypeViewRepository = shopTypeViewRepository;
        this.shopTypeRepository = shopTypeRepository;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public boolean isEnabled() {
        return viewStoreEnabled;
    }

    public boolean isActiveType(Long shopTypeId) {
        if (shopTypeId == null) {
            return false;
        }
        if (shopTypeRepository.findByIdAndActiveTrue(shopTypeId).isPresent()) {
            return true;
        }
        if (!viewStoreEnabled) {
            return false;
        }
        ShopTypeView document = shopTypeViewRepository.findById(shopTypeId).orElse(null);
        return document != null && document.isActive();
    }

    public List<ShopTypeView> findActiveTypes() {
        List<ShopTypeEntity> sqlTypes = shopTypeRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
        if (!sqlTypes.isEmpty()) {
            return sqlTypes.stream().map(this::toView).toList();
        }
        if (!viewStoreEnabled) {
            return List.of();
        }
        return shopTypeViewRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
    }

    private ShopTypeView toView(ShopTypeEntity entity) {
        ShopTypeView view = new ShopTypeView();
        view.setId(entity.getId());
        view.setName(entity.getName());
        view.setSortOrder(entity.getSortOrder());
        view.setActive(entity.isActive());
        return view;
    }
}
