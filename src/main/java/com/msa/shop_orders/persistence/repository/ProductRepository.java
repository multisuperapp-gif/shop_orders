package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByShopIdOrderByUpdatedAtDesc(Long shopId);
    Optional<ProductEntity> findByIdAndShopId(Long id, Long shopId);
    boolean existsBySkuIgnoreCase(String sku);
}
