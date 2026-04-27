package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.ShopEntity;
import com.msa.shop_orders.persistence.repository.ShopRepository;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopShellWriteService {
    private final ShopRepository shopRepository;
    private final ShopRuntimeSyncService shopRuntimeSyncService;

    public ShopShellWriteService(
            ShopRepository shopRepository,
            ShopRuntimeSyncService shopRuntimeSyncService
    ) {
        this.shopRepository = shopRepository;
        this.shopRuntimeSyncService = shopRuntimeSyncService;
    }

    @Transactional
    public ShopShellView updateOperationalStatus(Long shopId, String operationalStatus) {
        ShopEntity shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Shop not found.", HttpStatus.NOT_FOUND));
        shop.setOperationalStatus(operationalStatus == null ? null : operationalStatus.trim().toUpperCase());
        ShopEntity saved = shopRepository.save(shop);
        ShopShellView shell = toShellView(saved);
        shopRuntimeSyncService.syncShellAfterCommit(shell);
        return shell;
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
        shell.setAvgRating(shop.getAvgRating());
        shell.setTotalReviews(shop.getTotalReviews());
        return shell;
    }
}
