package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
}
