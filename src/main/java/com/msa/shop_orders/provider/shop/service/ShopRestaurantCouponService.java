package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopRestaurantCouponData;
import com.msa.shop_orders.provider.shop.dto.ShopRestaurantCouponUpdateRequest;

public interface ShopRestaurantCouponService {
    ShopRestaurantCouponData fetchCurrent();
    ShopRestaurantCouponData saveCurrent(ShopRestaurantCouponUpdateRequest request);
}
