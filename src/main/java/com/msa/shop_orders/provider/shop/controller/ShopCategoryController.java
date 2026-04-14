package com.msa.shop_orders.provider.shop.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.provider.shop.dto.ShopAvailableCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCreateCategoryRequest;
import com.msa.shop_orders.provider.shop.service.ShopCategoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shops/categories")
public class ShopCategoryController {
    private final ShopCategoryService shopCategoryService;

    public ShopCategoryController(ShopCategoryService shopCategoryService) {
        this.shopCategoryService = shopCategoryService;
    }

    @GetMapping("/available")
    public ApiResponse<List<ShopAvailableCategoryData>> availableCategories() {
        return ApiResponse.success(null, shopCategoryService.availableCategories());
    }

    @GetMapping
    public ApiResponse<List<ShopCategoryData>> categories() {
        return ApiResponse.success(null, shopCategoryService.categories());
    }

    @PostMapping
    public ApiResponse<ShopCategoryData> createCategory(@Valid @RequestBody ShopCreateCategoryRequest request) {
        return ApiResponse.success("Shop category saved successfully.", shopCategoryService.createCategory(request));
    }

    @PostMapping("/{categoryId}/add")
    public ApiResponse<ShopCategoryData> addCategory(@PathVariable Long categoryId) {
        return ApiResponse.success("Shop category added successfully.", shopCategoryService.addCategory(categoryId));
    }
}
