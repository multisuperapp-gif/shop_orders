package com.msa.shop_orders.provider.shop.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductActivityData;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.provider.shop.dto.ShopProductStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.service.ShopProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shops/products")
public class ShopProductController {
    private final ShopProductService shopProductService;

    public ShopProductController(ShopProductService shopProductService) {
        this.shopProductService = shopProductService;
    }

    @GetMapping
    public ApiResponse<List<ShopProductData>> products(@RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(null, shopProductService.products(categoryId));
    }

    @PostMapping
    public ApiResponse<ShopProductData> createProduct(@Valid @RequestBody ShopCreateProductRequest request) {
        return ApiResponse.success("Shop product saved successfully.", shopProductService.createProduct(request));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ShopProductData> updateProduct(@PathVariable Long productId, @Valid @RequestBody ShopCreateProductRequest request) {
        return ApiResponse.success("Shop product updated successfully.", shopProductService.updateProduct(productId, request));
    }

    @PostMapping("/{productId}/duplicate")
    public ApiResponse<ShopProductData> duplicateProduct(@PathVariable Long productId) {
        return ApiResponse.success("Shop product duplicated successfully.", shopProductService.duplicateProduct(productId));
    }

    @GetMapping("/{productId}/activity")
    public ApiResponse<List<ShopProductActivityData>> productActivity(@PathVariable Long productId) {
        return ApiResponse.success(null, shopProductService.productActivity(productId));
    }

    @PatchMapping("/{productId}/status")
    public ApiResponse<ShopProductData> updateProductStatus(@PathVariable Long productId, @Valid @RequestBody ShopProductStatusUpdateRequest request) {
        String message = Boolean.TRUE.equals(request.active())
                ? "Shop product reactivated successfully."
                : "Shop product archived successfully.";
        return ApiResponse.success(message, shopProductService.updateProductStatus(productId, request));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> removeProduct(@PathVariable Long productId) {
        shopProductService.removeProduct(productId);
        return ApiResponse.success("Shop product removed successfully.", null);
    }
}
