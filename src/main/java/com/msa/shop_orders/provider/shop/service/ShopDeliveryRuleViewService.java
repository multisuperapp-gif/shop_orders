package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopProductDeliveryRuleData;
import com.msa.shop_orders.provider.shop.view.ShopDeliveryRuleView;
import com.msa.shop_orders.provider.shop.view.repository.ShopDeliveryRuleViewRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class ShopDeliveryRuleViewService {
    private final ShopDeliveryRuleViewRepository shopDeliveryRuleViewRepository;
    private final ShopLocationViewService shopLocationViewService;

    public ShopDeliveryRuleViewService(
            ShopDeliveryRuleViewRepository shopDeliveryRuleViewRepository,
            ShopLocationViewService shopLocationViewService
    ) {
        this.shopDeliveryRuleViewRepository = shopDeliveryRuleViewRepository;
        this.shopLocationViewService = shopLocationViewService;
    }

    public Optional<ShopProductDeliveryRuleData> findPrimaryDeliveryRule(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        return shopDeliveryRuleViewRepository.findById(shopId)
                .map(this::toData)
                .or(() -> defaultRule(shopId));
    }

    public Optional<ShopProductDeliveryRuleData> refreshPrimaryDeliveryRule(Long shopId) {
        return findPrimaryDeliveryRule(shopId);
    }

    private Optional<ShopProductDeliveryRuleData> defaultRule(Long shopId) {
        return shopLocationViewService.findPrimaryLocationId(shopId)
                .map(locationId -> new ShopProductDeliveryRuleData(
                        locationId,
                        "DELIVERY",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("999999999.99"),
                        30,
                        60
                ));
    }

    private ShopProductDeliveryRuleData toData(ShopDeliveryRuleView document) {
        return new ShopProductDeliveryRuleData(
                document.getShopLocationId(),
                document.getDeliveryType(),
                document.getRadiusKm(),
                document.getMinOrderAmount(),
                document.getDeliveryFee(),
                document.getFreeDeliveryAbove(),
                document.getOrderCutoffMinutesBeforeClose(),
                document.getClosingSoonMinutes()
        );
    }
}
