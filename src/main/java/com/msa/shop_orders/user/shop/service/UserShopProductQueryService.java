package com.msa.shop_orders.user.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopProductData;

import java.util.List;

public interface UserShopProductQueryService {
    List<ShopProductData> products(Long shopId, Long categoryId, String search);
    ShopProductData product(Long productId);
}
