package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import com.msa.shop_orders.persistence.repository.ShopLocationRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ShopLocationViewService {
    private final ShopLocationRepository shopLocationRepository;

    public ShopLocationViewService(
            ShopLocationRepository shopLocationRepository
    ) {
        this.shopLocationRepository = shopLocationRepository;
    }

    public Optional<Long> findPrimaryLocationId(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        return shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shopId)
                .map(ShopLocationEntity::getId);
    }
}
