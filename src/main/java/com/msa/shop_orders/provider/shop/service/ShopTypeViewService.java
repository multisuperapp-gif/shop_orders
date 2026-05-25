package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.ShopTypeEntity;
import com.msa.shop_orders.persistence.repository.ShopTypeRepository;
import com.msa.shop_orders.provider.shop.view.ShopTypeView;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopTypeViewService {
    private final ShopTypeRepository shopTypeRepository;

    public ShopTypeViewService(
            ShopTypeRepository shopTypeRepository
    ) {
        this.shopTypeRepository = shopTypeRepository;
    }

    public boolean isEnabled() {
        return false;
    }

    public boolean isActiveType(Long shopTypeId) {
        if (shopTypeId == null) {
            return false;
        }
        return shopTypeRepository.findByIdAndActiveTrue(shopTypeId).isPresent();
    }

    public List<ShopTypeView> findActiveTypes() {
        List<ShopTypeEntity> sqlTypes = shopTypeRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
        return sqlTypes.stream().map(this::toView).toList();
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
