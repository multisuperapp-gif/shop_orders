package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.ShopEntity;
import com.msa.shop_orders.persistence.repository.ShopRepository;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopCategoryViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopOperatingHoursViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ShopShellViewService {
    private final ShopShellViewRepository shopShellViewRepository;
    private final ShopRepository shopRepository;
    private final ShopCategoryViewRepository shopCategoryViewRepository;
    private final ShopProductViewRepository shopProductViewRepository;
    private final ShopOperatingHoursViewRepository shopOperatingHoursViewRepository;

    public ShopShellViewService(
            ShopShellViewRepository shopShellViewRepository,
            ShopRepository shopRepository,
            ShopCategoryViewRepository shopCategoryViewRepository,
            ShopProductViewRepository shopProductViewRepository,
            ShopOperatingHoursViewRepository shopOperatingHoursViewRepository
    ) {
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopRepository = shopRepository;
        this.shopCategoryViewRepository = shopCategoryViewRepository;
        this.shopProductViewRepository = shopProductViewRepository;
        this.shopOperatingHoursViewRepository = shopOperatingHoursViewRepository;
    }

    public Optional<ShopShellView> findCurrentApprovedShop(Long ownerUserId) {
        if (ownerUserId == null) {
            return Optional.empty();
        }
        return shopRepository
                .findFirstByOwnerUserIdAndApprovalStatusIgnoreCaseOrderByIdDesc(ownerUserId, "APPROVED")
                .or(() -> shopRepository.findFirstByOwnerUserIdOrderByIdDesc(ownerUserId))
                .map(shop -> {
                    ShopShellView shell = toShellViewAndSync(shop);
                    // Heal existing shops: if categories + products are already present
                    // but businessSetupComplete was never set (e.g. added before this
                    // feature was deployed), mark it on the shop owner's next login.
                    checkAndUpdateBusinessSetupComplete(shell.getShopId());
                    return shell;
                });
    }

    public Optional<ShopShellView> findByShopId(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        return shopRepository.findById(shopId)
                .map(this::toShellViewAndSync);
    }

    public void syncShellView(ShopShellView document) {
        if (document == null || document.getShopId() == null) {
            return;
        }
        // Preserve the MongoDB-managed rating: the incoming document is rebuilt from
        // the SQL shop (which doesn't track order ratings), so carry over the existing
        // recomputed avgRating / totalReviews instead of resetting them to 0.
        shopShellViewRepository.findById(document.getShopId()).ifPresent(existing -> {
            if (existing.getAvgRating() != null) {
                document.setAvgRating(existing.getAvgRating());
            }
            if (existing.getTotalReviews() != null) {
                document.setTotalReviews(existing.getTotalReviews());
            }
        });
        shopShellViewRepository.save(document);
    }

    /**
     * Recomputes and persists businessSetupComplete for the given shop.
     * A shop is considered set up when it has ≥1 enabled category AND ≥1 active product
     * AND operating hours configured for at least one weekday (i.e. not closed on every day).
     * Called automatically after any category, product or operating-hours write operation.
     */
    public void checkAndUpdateBusinessSetupComplete(Long shopId) {
        if (shopId == null) {
            return;
        }
        shopShellViewRepository.findById(shopId).ifPresent(shell -> {
            boolean hasEnabledCategory = !shopCategoryViewRepository.findByShopIdAndEnabledTrue(shopId).isEmpty();
            boolean hasActiveProduct = shopProductViewRepository.findByShopIdOrderByUpdatedAtDesc(shopId)
                    .stream().anyMatch(p -> p.isActive());
            // At least one weekday must be open with a valid open/close time —
            // if every day is disabled (or no timing rows exist) the setup is incomplete.
            boolean hasOperatingHours = shopOperatingHoursViewRepository.findByShopIdOrderByWeekdayAsc(shopId)
                    .stream()
                    .anyMatch(row -> !row.isClosed()
                            && row.getOpenTime() != null && !row.getOpenTime().isBlank()
                            && row.getCloseTime() != null && !row.getCloseTime().isBlank());
            boolean complete = hasEnabledCategory && hasActiveProduct && hasOperatingHours;
            if (!Boolean.valueOf(complete).equals(shell.getBusinessSetupComplete())) {
                shell.setBusinessSetupComplete(complete);
                shopShellViewRepository.save(shell);
            }
        });
    }

    // Reads existing MongoDB doc to preserve MongoDB-only fields (businessSetupComplete,
    // restaurantCoupon), overlays fresh SQL values, then saves back to MongoDB so that
    // any approval-status change in the auth-service is immediately reflected.
    private ShopShellView toShellViewAndSync(ShopEntity shop) {
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
        // avgRating / totalReviews are MongoDB-managed (recomputeShopRating writes them
        // from the shop's order reviews) — preserve the existing values instead of
        // overwriting with the SQL shop, which never tracks order ratings.
        if (shell.getAvgRating() == null) {
            shell.setAvgRating(BigDecimal.ZERO);
        }
        if (shell.getTotalReviews() == null) {
            shell.setTotalReviews(0);
        }
        // businessSetupComplete is intentionally preserved from MongoDB —
        // it is managed exclusively by checkAndUpdateBusinessSetupComplete().
        shopShellViewRepository.save(shell);
        return shell;
    }
}
