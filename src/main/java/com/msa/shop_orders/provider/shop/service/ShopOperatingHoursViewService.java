package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.view.ShopOperatingHoursView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOperatingHoursViewRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ShopOperatingHoursViewService {
    private final ShopOperatingHoursViewRepository repository;

    public ShopOperatingHoursViewService(ShopOperatingHoursViewRepository repository) {
        this.repository = repository;
    }

    public List<ShopOperatingHoursView> findByShopId(Long shopId) {
        if (shopId == null) {
            return List.of();
        }
        return repository.findByShopIdOrderByWeekdayAsc(shopId);
    }

    public Optional<ShopOperatingHoursView> findByShopIdAndWeekday(Long shopId, Integer weekday) {
        if (shopId == null || weekday == null) {
            return Optional.empty();
        }
        return repository.findByShopIdAndWeekday(shopId, weekday);
    }

    public List<ShopOperatingHoursView> saveAll(List<ShopOperatingHoursView> documents) {
        return repository.saveAll(documents);
    }
}
