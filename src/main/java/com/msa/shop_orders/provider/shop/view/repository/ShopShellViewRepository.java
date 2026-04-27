package com.msa.shop_orders.provider.shop.view.repository;

import com.msa.shop_orders.provider.shop.view.ShopShellView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShopShellViewRepository extends MongoRepository<ShopShellView, Long> {
    Optional<ShopShellView> findFirstByOwnerUserId(Long ownerUserId);
    List<ShopShellView> findByApprovalStatus(String approvalStatus);
    List<ShopShellView> findByApprovalStatusAndShopTypeId(String approvalStatus, Long shopTypeId);
}
