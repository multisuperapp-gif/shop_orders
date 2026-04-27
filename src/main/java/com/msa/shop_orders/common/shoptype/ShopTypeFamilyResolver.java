package com.msa.shop_orders.common.shoptype;

import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.ShopTypeView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopTypeViewRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ShopTypeFamilyResolver {
    private final ShopTypeViewRepository shopTypeViewRepository;
    private final ShopShellViewRepository shopShellViewRepository;

    public ShopTypeFamilyResolver(
            ShopTypeViewRepository shopTypeViewRepository,
            ShopShellViewRepository shopShellViewRepository
    ) {
        this.shopTypeViewRepository = shopTypeViewRepository;
        this.shopShellViewRepository = shopShellViewRepository;
    }

    public ShopTypeFamily resolveFamily(ShopShellView shop) {
        if (shop == null) {
            return ShopTypeFamily.SHARED;
        }
        return resolveFamily(shop.getShopTypeId());
    }

    public ShopTypeFamily resolveFamilyByShopId(Long shopId) {
        if (shopId == null) {
            return ShopTypeFamily.SHARED;
        }
        return shopShellViewRepository.findById(shopId)
                .map(this::resolveFamily)
                .orElse(ShopTypeFamily.SHARED);
    }

    public ShopTypeFamily resolveFamily(Long shopTypeId) {
        if (shopTypeId == null) {
            return ShopTypeFamily.SHARED;
        }
        return shopTypeViewRepository.findById(shopTypeId)
                .map(this::resolveFamily)
                .orElse(ShopTypeFamily.SHARED);
    }

    public ShopTypeFamily resolveFamily(ShopTypeView shopType) {
        if (shopType == null || shopType.getName() == null) {
            return ShopTypeFamily.SHARED;
        }
        String normalized = shopType.getName().trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("restaurant") || normalized.contains("restuarant") || normalized.contains("food") || normalized.contains("cafe")) {
            return ShopTypeFamily.RESTAURANT;
        }
        if (normalized.contains("grocery") || normalized.contains("groceries") || normalized.contains("mart") || normalized.contains("supermarket")) {
            return ShopTypeFamily.GROCERY;
        }
        if (normalized.contains("pharmacy") || normalized.contains("medical") || normalized.contains("medicine")) {
            return ShopTypeFamily.PHARMACY;
        }
        if (normalized.contains("fashion") || normalized.contains("clothing") || normalized.contains("apparel") || normalized.contains("boutique")) {
            return ShopTypeFamily.FASHION;
        }
        if (normalized.contains("footwear") || normalized.contains("shoe") || normalized.contains("shoes") || normalized.contains("sneaker")) {
            return ShopTypeFamily.FOOTWEAR;
        }
        if (normalized.contains("gift") || normalized.contains("gifting") || normalized.contains("toy") || normalized.contains("stationery")) {
            return ShopTypeFamily.GIFT;
        }
        return ShopTypeFamily.SHARED;
    }
}
