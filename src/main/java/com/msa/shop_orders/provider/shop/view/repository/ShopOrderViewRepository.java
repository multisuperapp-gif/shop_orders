package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ShopOrderViewRepository extends MongoRepository<ShopOrderView, Long> {
    List<ShopOrderView> findByShopIdOrderByCreatedAtDesc(Long shopId);
    List<ShopOrderView> findByUserIdOrderByCreatedAtDesc(Long userId);
    // Accept-first 5-minute payment window: accepted-but-unpaid orders whose
    // acceptance (updatedAt) is older than the cutoff are auto-cancelled.
    List<ShopOrderView> findByOrderStatusAndUpdatedAtBefore(String orderStatus, LocalDateTime cutoff);
    void deleteByShopId(Long shopId);
}
