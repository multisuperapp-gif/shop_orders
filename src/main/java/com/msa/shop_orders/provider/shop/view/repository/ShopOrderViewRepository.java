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
    // Same window, but covering both ACCEPTED and PAYMENT_PENDING (payment was
    // started but never completed — e.g. Razorpay sheet abandoned).
    List<ShopOrderView> findByOrderStatusInAndUpdatedAtBefore(List<String> orderStatuses, LocalDateTime cutoff);
    // End-of-day auto-delivery: paid orders still open (placed on a prior day)
    // are auto-marked delivered, mirroring labour/service end-of-day completion.
    List<ShopOrderView> findByOrderStatusInAndCreatedAtBefore(List<String> orderStatuses, LocalDateTime cutoff);
    void deleteByShopId(Long shopId);
}
