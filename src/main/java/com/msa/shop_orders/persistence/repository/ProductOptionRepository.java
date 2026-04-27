package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ProductOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ProductOptionRepository extends JpaRepository<ProductOptionEntity, Long> {
    @Query("""
            select option
            from ProductOptionEntity option
            join ProductOptionGroupEntity group on group.id = option.optionGroupId
            where option.id in :optionIds
              and option.active = true
              and group.productId = :productId
              and group.active = true
            order by option.id asc
            """)
    List<ProductOptionEntity> findActiveOptionsForProduct(Collection<Long> optionIds, Long productId);
}
