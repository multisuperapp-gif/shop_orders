package com.msa.shop_orders.consumer.shop.service;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.common.shoptype.ShopTypeFamilyResolver;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopCategoryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopDetailData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopSummaryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopTypeData;
import com.msa.shop_orders.consumer.shop.type.ConsumerShopTypeRegistry;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConsumerShopCatalogService {
    private final ShopTypeFamilyResolver shopTypeFamilyResolver;
    private final ConsumerShopTypeRegistry consumerShopTypeRegistry;

    public ConsumerShopCatalogService(
            ShopTypeFamilyResolver shopTypeFamilyResolver,
            ConsumerShopTypeRegistry consumerShopTypeRegistry
    ) {
        this.shopTypeFamilyResolver = shopTypeFamilyResolver;
        this.consumerShopTypeRegistry = consumerShopTypeRegistry;
    }

    public List<ConsumerShopTypeData> shopTypes() {
        return consumerShopTypeRegistry.resolve(ShopTypeFamily.SHARED).shopTypes();
    }

    public List<ConsumerShopSummaryData> shops(Long shopTypeId, String search) {
        return consumerShopTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shopTypeId))
                .shops(shopTypeId, search);
    }

    public ConsumerShopDetailData shopDetail(Long shopId) {
        return consumerShopTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamilyByShopId(shopId))
                .shopDetail(shopId);
    }

    public List<ConsumerShopCategoryData> shopCategories(Long shopId) {
        return consumerShopTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamilyByShopId(shopId))
                .shopCategories(shopId);
    }

    public List<ShopProductData> shopProducts(Long shopId, Long categoryId) {
        return consumerShopTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamilyByShopId(shopId))
                .shopProducts(shopId, categoryId);
    }

    public ShopProductData shopProductDetail(Long shopId, Long productId) {
        return consumerShopTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamilyByShopId(shopId))
                .shopProductDetail(shopId, productId);
    }
}
