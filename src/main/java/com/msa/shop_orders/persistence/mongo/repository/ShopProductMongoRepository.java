package com.msa.shop_orders.persistence.mongo.repository;

import com.msa.shop_orders.persistence.mongo.document.ShopProductDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ShopProductMongoRepository extends MongoRepository<ShopProductDocument, String> {
    List<ShopProductDocument> findByShopIdOrderByUpdatedAtDesc(Long shopId);
    List<ShopProductDocument> findByShopIdAndShopCategoryIdOrderByUpdatedAtDesc(Long shopId, Long shopCategoryId);
    List<ShopProductDocument> findByShopIdAndShopCategoryIdAndActiveTrueOrderByUpdatedAtDesc(Long shopId, Long shopCategoryId);
    List<ShopProductDocument> findByShopIdAndActiveTrueOrderByUpdatedAtDesc(Long shopId);
    Optional<ShopProductDocument> findByShopIdAndProductId(Long shopId, Long productId);
    Optional<ShopProductDocument> findByProductIdAndActiveTrue(Long productId);
    boolean existsBySku(String sku);
}
