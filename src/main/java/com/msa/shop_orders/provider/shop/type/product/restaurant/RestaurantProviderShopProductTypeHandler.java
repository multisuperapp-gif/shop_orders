package com.msa.shop_orders.provider.shop.type.product.restaurant;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.provider.shop.dto.ShopProductStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.type.product.ProviderShopProductTypeHandler;
import com.msa.shop_orders.provider.shop.type.product.shared.SharedProviderShopProductTypeHandler;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RestaurantProviderShopProductTypeHandler implements ProviderShopProductTypeHandler {
    private final SharedProviderShopProductTypeHandler delegate;
    private final RestaurantProductRequestNormalizer requestNormalizer;

    public RestaurantProviderShopProductTypeHandler(
            SharedProviderShopProductTypeHandler delegate,
            RestaurantProductRequestNormalizer requestNormalizer
    ) {
        this.delegate = delegate;
        this.requestNormalizer = requestNormalizer;
    }

    @Override
    public ShopTypeFamily family() {
        return ShopTypeFamily.RESTAURANT;
    }

    @Override
    public List<ShopProductData> products(ShopShellView shop, Long categoryId) {
        return delegate.products(shop, categoryId);
    }

    @Override
    public ShopProductData createProduct(ShopShellView shop, ShopCreateProductRequest request) {
        return delegate.createProduct(shop, requestNormalizer.normalize(shop, request));
    }

    @Override
    public ShopProductData updateProduct(ShopShellView shop, Long productId, ShopCreateProductRequest request) {
        return delegate.updateProduct(shop, productId, requestNormalizer.normalize(shop, request));
    }

    @Override
    public ShopProductData duplicateProduct(ShopShellView shop, Long productId) {
        return delegate.duplicateProduct(shop, productId);
    }

    @Override
    public ShopProductData updateProductStatus(ShopShellView shop, Long productId, ShopProductStatusUpdateRequest request) {
        return delegate.updateProductStatus(shop, productId, request);
    }
}
