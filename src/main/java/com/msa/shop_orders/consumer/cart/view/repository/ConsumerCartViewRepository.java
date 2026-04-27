package com.msa.shop_orders.consumer.cart.view.repository;

import com.msa.shop_orders.consumer.cart.view.ConsumerCartView;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConsumerCartViewRepository extends MongoRepository<ConsumerCartView, Long> {
}
