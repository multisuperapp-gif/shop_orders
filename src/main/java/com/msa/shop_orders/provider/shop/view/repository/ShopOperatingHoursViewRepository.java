package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopOperatingHoursView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ShopOperatingHoursViewRepository extends MongoRepository<ShopOperatingHoursView, String> {
    List<ShopOperatingHoursView> findByShopIdOrderByWeekdayAsc(Long shopId);
    Optional<ShopOperatingHoursView> findByShopIdAndWeekday(Long shopId, Integer weekday);
    void deleteByShopId(Long shopId);
}
