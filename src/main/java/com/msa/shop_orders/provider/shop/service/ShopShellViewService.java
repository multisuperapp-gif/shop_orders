package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.ShopEntity;
import com.msa.shop_orders.persistence.repository.ShopRepository;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ShopShellViewService {
    private final ShopShellViewRepository shopShellViewRepository;
    private final ShopRepository shopRepository;

    public ShopShellViewService(
            ShopShellViewRepository shopShellViewRepository,
            ShopRepository shopRepository
    ) {
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopRepository = shopRepository;
    }

    public Optional<ShopShellView> findCurrentApprovedShop(Long ownerUserId) {
        if (ownerUserId == null) {
            return Optional.empty();
        }
        return shopRepository
                .findFirstByOwnerUserIdAndApprovalStatusIgnoreCaseOrderByIdDesc(
                        ownerUserId,
                        "APPROVED"
                )
                .or(() -> shopRepository.findFirstByOwnerUserIdOrderByIdDesc(ownerUserId))
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
        if (document == null || document.getShopId() == null) {
            return;
        }
        shopShellViewRepository.save(document);
    }

    private ShopShellView toShellView(ShopEntity shop) {
        ShopShellView shell = shopShellViewRepository.findById(shop.getId())
                .orElseGet(ShopShellView::new);
        shell.setShopId(shop.getId());
        shell.setOwnerUserId(shop.getOwnerUserId());
        shell.setShopCode(shop.getShopCode());
        shell.setOwnerName(shop.getOwnerName());
        shell.setShopName(shop.getShopName());
        shell.setShopTypeId(shop.getShopTypeId());
        shell.setLogoFileId(shop.getLogoFileId());
        shell.setCoverFileId(shop.getCoverFileId());
        shell.setRestaurantServiceType(shop.getRestaurantServiceType());
        shell.setApprovalStatus(shop.getApprovalStatus());
        shell.setOperationalStatus(shop.getOperationalStatus());
        shell.setAvgRating(shop.getAvgRating() == null ? BigDecimal.ZERO : shop.getAvgRating());
        shell.setTotalReviews(shop.getTotalReviews() == null ? 0 : shop.getTotalReviews());
        return shell;
    }
}
