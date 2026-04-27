package com.msa.shop_orders.provider.shop.type.product.grocery;

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
public class GroceryProviderShopProductTypeHandler implements ProviderShopProductTypeHandler {
    private final SharedProviderShopProductTypeHandler delegate;
    public GroceryProviderShopProductTypeHandler(SharedProviderShopProductTypeHandler delegate) { this.delegate = delegate; }
    public ShopTypeFamily family() { return ShopTypeFamily.GROCERY; }
    public List<ShopProductData> products(ShopShellView shop, Long categoryId) { return delegate.products(shop, categoryId); }
    public ShopProductData createProduct(ShopShellView shop, ShopCreateProductRequest request) { return delegate.createProduct(shop, request); }
    public ShopProductData updateProduct(ShopShellView shop, Long productId, ShopCreateProductRequest request) { return delegate.updateProduct(shop, productId, request); }
    public ShopProductData duplicateProduct(ShopShellView shop, Long productId) { return delegate.duplicateProduct(shop, productId); }
    public ShopProductData updateProductStatus(ShopShellView shop, Long productId, ShopProductStatusUpdateRequest request) { return delegate.updateProductStatus(shop, productId, request); }
}
