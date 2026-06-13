package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.common.shoptype.ShopTypeFamilyResolver;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Restaurant menus start fresh every day. Unlike grocery/pharmacy shops that
 * track real stock counts, a restaurant only marks items "out of stock for
 * today" (Swiggy/Zomato style). This job runs each morning and flips every
 * listed restaurant item back to available, so yesterday's out-of-stock marks
 * are cleared and the day starts with the full menu.
 */
@Component
public class RestaurantAvailabilityResetScheduler {
    private static final Logger log = LoggerFactory.getLogger(RestaurantAvailabilityResetScheduler.class);

    private final ShopShellViewRepository shopShellViewRepository;
    private final ShopTypeFamilyResolver shopTypeFamilyResolver;
    private final ShopProductWriteService shopProductWriteService;

    public RestaurantAvailabilityResetScheduler(
            ShopShellViewRepository shopShellViewRepository,
            ShopTypeFamilyResolver shopTypeFamilyResolver,
            ShopProductWriteService shopProductWriteService
    ) {
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopTypeFamilyResolver = shopTypeFamilyResolver;
        this.shopProductWriteService = shopProductWriteService;
    }

    // Daily at 04:00 IST — a quiet hour when no shops are open, so resetting
    // availability can't collide with a live order.
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Kolkata")
    public void resetRestaurantAvailability() {
        List<ShopShellView> shops;
        try {
            shops = shopShellViewRepository.findAll();
        } catch (Exception exception) {
            log.warn("Failed to load shops for daily restaurant availability reset", exception);
            return;
        }
        int shopsReset = 0;
        int itemsReset = 0;
        for (ShopShellView shop : shops) {
            try {
                if (shopTypeFamilyResolver.resolveFamily(shop) != ShopTypeFamily.RESTAURANT) {
                    continue;
                }
                int count = shopProductWriteService.resetAvailabilityForShop(shop.getShopId());
                shopsReset++;
                itemsReset += count;
            } catch (Exception exception) {
                log.warn("Failed to reset availability for restaurant shop {}", shop.getShopId(), exception);
            }
        }
        if (shopsReset > 0) {
            log.info("Daily restaurant availability reset: {} items re-stocked across {} restaurant shops",
                    itemsReset, shopsReset);
        }
    }
}
