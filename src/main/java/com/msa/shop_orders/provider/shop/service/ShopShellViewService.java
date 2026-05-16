package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.ShopEntity;
import com.msa.shop_orders.persistence.repository.ShopRepository;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ShopShellViewService {
    private final ShopShellViewRepository shopShellViewRepository;
    private final ShopRepository shopRepository;
    private final boolean viewStoreEnabled;

    public ShopShellViewService(
            ShopShellViewRepository shopShellViewRepository,
            ShopRepository shopRepository,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopRepository = shopRepository;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public Optional<ShopShellView> findCurrentApprovedShop(Long ownerUserId) {
        if (ownerUserId == null) {
            return Optional.empty();
        }
        return shopRepository.findFirstByOwnerUserIdOrderByIdDesc(ownerUserId)
                .map(this::toShellView);
    }

    public Optional<ShopShellView> findByShopId(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        return shopRepository.findById(shopId)
                .map(this::toShellView);
    }

    public void syncShellView(ShopShellView document) {
        if (!viewStoreEnabled || document == null || document.getShopId() == null) {
            return;
        }
        shopShellViewRepository.save(document);
    }

    private ShopShellView toShellView(ShopEntity shop) {
        ShopShellView shell = new ShopShellView();
        shell.setShopId(shop.getId());
        shell.setOwnerUserId(shop.getOwnerUserId());
        shell.setShopCode(shop.getShopCode());
        shell.setOwnerName(shop.getOwnerName());
        shell.setShopName(shop.getShopName());
        shell.setShopTypeId(shop.getShopTypeId());
        shell.setLogoFileId(shop.getLogoFileId());
        shell.setCoverFileId(shop.getCoverFileId());
        shell.setApprovalStatus(shop.getApprovalStatus());
        shell.setOperationalStatus(shop.getOperationalStatus());
        shell.setAvgRating(shop.getAvgRating() == null ? BigDecimal.ZERO : shop.getAvgRating());
        shell.setTotalReviews(shop.getTotalReviews() == null ? 0 : shop.getTotalReviews());
        return shell;
    }
}
