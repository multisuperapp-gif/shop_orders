package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopAvailableCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCreateCategoryRequest;

import java.util.List;

public interface ShopCategoryService {
    List<ShopAvailableCategoryData> availableCategories();
    List<ShopCategoryData> categories();
    ShopCategoryData createCategory(ShopCreateCategoryRequest request);
    ShopCategoryData addCategory(Long categoryId);
}
