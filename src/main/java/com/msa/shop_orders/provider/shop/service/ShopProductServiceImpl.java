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
    private final ShopShellViewService shopShellViewService;
    private final ShopProductWriteService shopProductWriteService;

    public ShopProductServiceImpl(
            ShopContextService shopContextService,
            ShopTypeFamilyResolver shopTypeFamilyResolver,
            ProviderShopProductTypeRegistry providerShopProductTypeRegistry,
            ShopShellViewService shopShellViewService,
            ShopProductWriteService shopProductWriteService
    ) {
        this.shopContextService = shopContextService;
        this.shopTypeFamilyResolver = shopTypeFamilyResolver;
        this.providerShopProductTypeRegistry = providerShopProductTypeRegistry;
        this.shopShellViewService = shopShellViewService;
        this.shopProductWriteService = shopProductWriteService;
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
        ShopProductData result = providerShopProductTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shop))
                .createProduct(shop, request);
        shopShellViewService.checkAndUpdateBusinessSetupComplete(shop.getShopId());
        return result;
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
        ShopProductData result = providerShopProductTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shop))
                .duplicateProduct(shop, productId);
        shopShellViewService.checkAndUpdateBusinessSetupComplete(shop.getShopId());
        return result;
    }

    @Override
    @Transactional
    public ShopProductData updateProductStatus(Long productId, ShopProductStatusUpdateRequest request) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        ShopProductData result = providerShopProductTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shop))
                .updateProductStatus(shop, productId, request);
        shopShellViewService.checkAndUpdateBusinessSetupComplete(shop.getShopId());
        return result;
    }

    // Availability is shop-type-agnostic (just flips quantityAvailable), so it
    // bypasses the per-type product handlers.
    @Override
    @Transactional
    public void updateProductAvailability(Long productId, boolean available) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        shopProductWriteService.updateProductAvailability(productId, shop.getShopId(), available);
    }

    @Override
    @Transactional
    public void updateVariantAvailability(Long productId, Long variantId, boolean available) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        shopProductWriteService.updateVariantAvailability(productId, variantId, shop.getShopId(), available);
    }
}
