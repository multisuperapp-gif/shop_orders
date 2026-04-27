package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopTypeView;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShopTypeViewRepository extends MongoRepository<ShopTypeView, Long> {
    List<ShopTypeView> findByActiveTrueOrderBySortOrderAscNameAsc();
}
