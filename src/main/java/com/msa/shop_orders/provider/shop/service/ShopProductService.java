package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductActivityData;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.provider.shop.dto.ShopProductStatusUpdateRequest;

import java.util.List;

public interface ShopProductService {
    List<ShopProductData> products(Long categoryId);
    ShopProductData createProduct(ShopCreateProductRequest request);
    ShopProductData updateProduct(Long productId, ShopCreateProductRequest request);
    ShopProductData duplicateProduct(Long productId);
    ShopProductData updateProductStatus(Long productId, ShopProductStatusUpdateRequest request);
    void removeProduct(Long productId);
    List<ShopProductActivityData> productActivity(Long productId);
}
