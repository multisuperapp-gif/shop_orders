package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.shoptype.ShopTypeFamilyResolver;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.provider.shop.dto.ShopProductStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.type.product.ProviderShopProductTypeRegistry;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ShopProductServiceImpl implements ShopProductService {
    private final ShopContextService shopContextService;
    private final ShopTypeFamilyResolver shopTypeFamilyResolver;
    private final ProviderShopProductTypeRegistry providerShopProductTypeRegistry;

    public ShopProductServiceImpl(
            ShopContextService shopContextService,
            ShopTypeFamilyResolver shopTypeFamilyResolver,
            ProviderShopProductTypeRegistry providerShopProductTypeRegistry
    ) {
        this.shopContextService = shopContextService;
        this.shopTypeFamilyResolver = shopTypeFamilyResolver;
        this.providerShopProductTypeRegistry = providerShopProductTypeRegistry;
    }

    @Override
    public List<ShopProductData> products(Long categoryId) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        return providerShopProductTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shop))
                .products(shop, categoryId);
    }

    @Override
    @Transactional
    public ShopProductData createProduct(ShopCreateProductRequest request) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        return providerShopProductTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shop))
                .createProduct(shop, request);
    }

    @Override
    @Transactional
    public ShopProductData updateProduct(Long productId, ShopCreateProductRequest request) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        return providerShopProductTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shop))
                .updateProduct(shop, productId, request);
    }

    @Override
    @Transactional
    public ShopProductData duplicateProduct(Long productId) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        return providerShopProductTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shop))
                .duplicateProduct(shop, productId);
    }

    @Override
    @Transactional
    public ShopProductData updateProductStatus(Long productId, ShopProductStatusUpdateRequest request) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        return providerShopProductTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shop))
                .updateProductStatus(shop, productId, request);
    }
}
