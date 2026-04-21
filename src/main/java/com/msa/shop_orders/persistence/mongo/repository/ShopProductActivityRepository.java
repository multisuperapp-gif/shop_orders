package com.msa.shop_orders.persistence.mongo.repository;

import com.msa.shop_orders.persistence.mongo.document.ShopProductActivityDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ShopProductActivityRepository extends MongoRepository<ShopProductActivityDocument, String> {
    List<ShopProductActivityDocument> findByShopIdAndProductIdOrderByCreatedAtDesc(Long shopId, Long productId);
}
