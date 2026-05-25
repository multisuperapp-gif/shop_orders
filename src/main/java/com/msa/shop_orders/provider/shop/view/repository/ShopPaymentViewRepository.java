package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopPaymentView;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ShopPaymentViewRepository extends MongoRepository<ShopPaymentView, Long> {
    Optional<ShopPaymentView> findByPaymentCode(String paymentCode);
}
