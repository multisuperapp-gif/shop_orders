package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, Long> {
}
