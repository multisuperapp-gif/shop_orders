package com.msa.shop_orders.provider.shop.type.product;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.provider.shop.dto.ShopProductStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.view.ShopShellView;

import java.util.List;

public interface ProviderShopProductTypeHandler {
    ShopTypeFamily family();
    List<ShopProductData> products(ShopShellView shop, Long categoryId);
    ShopProductData createProduct(ShopShellView shop, ShopCreateProductRequest request);
    ShopProductData updateProduct(ShopShellView shop, Long productId, ShopCreateProductRequest request);
    ShopProductData duplicateProduct(ShopShellView shop, Long productId);
    ShopProductData updateProductStatus(ShopShellView shop, Long productId, ShopProductStatusUpdateRequest request);
}
