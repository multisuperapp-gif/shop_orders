package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.ShopDeliveryRuleEntity;
import com.msa.shop_orders.persistence.repository.ShopDeliveryRuleRepository;
import com.msa.shop_orders.provider.shop.dto.ShopProductDeliveryRuleData;
import com.msa.shop_orders.provider.shop.view.ShopDeliveryRuleView;
import com.msa.shop_orders.provider.shop.view.repository.ShopDeliveryRuleViewRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ShopDeliveryRuleViewService {
    private final ShopDeliveryRuleViewRepository shopDeliveryRuleViewRepository;
    private final ShopLocationViewService shopLocationViewService;
    private final ShopDeliveryRuleRepository shopDeliveryRuleRepository;
    private final boolean viewStoreEnabled;

    public ShopDeliveryRuleViewService(
            ShopDeliveryRuleViewRepository shopDeliveryRuleViewRepository,
            ShopLocationViewService shopLocationViewService,
            ShopDeliveryRuleRepository shopDeliveryRuleRepository,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopDeliveryRuleViewRepository = shopDeliveryRuleViewRepository;
        this.shopLocationViewService = shopLocationViewService;
        this.shopDeliveryRuleRepository = shopDeliveryRuleRepository;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public Optional<ShopProductDeliveryRuleData> findPrimaryDeliveryRule(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        if (!viewStoreEnabled) {
            return loadFromSql(shopId, false);
        }
        ShopDeliveryRuleView document = shopDeliveryRuleViewRepository.findById(shopId).orElse(null);
        if (document != null) {
            return Optional.of(toData(document));
        }
        return loadFromSql(shopId, true);
    }

    public Optional<ShopProductDeliveryRuleData> refreshPrimaryDeliveryRule(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        return loadFromSql(shopId, viewStoreEnabled);
    }

    private Optional<ShopProductDeliveryRuleData> loadFromSql(Long shopId, boolean syncToMongo) {
        Optional<Long> locationId = resolvePrimaryLocationId(shopId);
        if (locationId.isEmpty()) {
            return Optional.empty();
        }
        Optional<ShopProductDeliveryRuleData> data = shopDeliveryRuleRepository.findByShopLocationId(locationId.get())
                .map(rule -> toData(locationId.get(), rule));
        if (syncToMongo) {
            if (data.isPresent()) {
                shopDeliveryRuleViewRepository.save(toDocument(shopId, data.get()));
            } else {
                shopDeliveryRuleViewRepository.deleteById(shopId);
            }
        }
        return data;
    }

    private Optional<Long> resolvePrimaryLocationId(Long shopId) {
        return shopLocationViewService.findPrimaryLocationId(shopId);
    }

    private ShopProductDeliveryRuleData toData(Long shopLocationId, ShopDeliveryRuleEntity rule) {
        return new ShopProductDeliveryRuleData(
                shopLocationId,
                rule.getDeliveryType(),
                rule.getRadiusKm(),
                rule.getMinOrderAmount(),
                rule.getDeliveryFee(),
                rule.getFreeDeliveryAbove(),
                rule.getOrderCutoffMinutesBeforeClose(),
                rule.getClosingSoonMinutes()
        );
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

    private ShopDeliveryRuleView toDocument(Long shopId, ShopProductDeliveryRuleData data) {
        ShopDeliveryRuleView document = new ShopDeliveryRuleView();
        document.setShopId(shopId);
        document.setShopLocationId(data.shopLocationId());
        document.setDeliveryType(data.deliveryType());
        document.setRadiusKm(data.radiusKm());
        document.setMinOrderAmount(data.minOrderAmount());
        document.setDeliveryFee(data.deliveryFee());
        document.setFreeDeliveryAbove(data.freeDeliveryAbove());
        document.setOrderCutoffMinutesBeforeClose(data.orderCutoffMinutesBeforeClose());
        document.setClosingSoonMinutes(data.closingSoonMinutes());
        return document;
    }
}
