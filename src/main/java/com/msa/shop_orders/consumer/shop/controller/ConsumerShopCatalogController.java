package com.msa.shop_orders.consumer.shop.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopCategoryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopDetailData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopSummaryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopTypeData;
import com.msa.shop_orders.consumer.shop.service.ConsumerShopCatalogService;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop-orders")
public class ConsumerShopCatalogController {
    private final ConsumerShopCatalogService consumerShopCatalogService;

    public ConsumerShopCatalogController(ConsumerShopCatalogService consumerShopCatalogService) {
        this.consumerShopCatalogService = consumerShopCatalogService;
    }

    @GetMapping("/shop-types")
    public ApiResponse<List<ConsumerShopTypeData>> shopTypes() {
        return ApiResponse.success(null, consumerShopCatalogService.shopTypes());
    }

    @GetMapping("/shops")
    public ApiResponse<List<ConsumerShopSummaryData>> shops(
            @RequestParam(required = false) Long shopTypeId,
            @RequestParam(required = false) String search
    ) {
        return ApiResponse.success(null, consumerShopCatalogService.shops(shopTypeId, search));
    }

    @GetMapping("/shops/{shopId}")
    public ApiResponse<ConsumerShopDetailData> shopDetail(@PathVariable Long shopId) {
        return ApiResponse.success(null, consumerShopCatalogService.shopDetail(shopId));
    }

    @GetMapping("/shops/{shopId}/categories")
    public ApiResponse<List<ConsumerShopCategoryData>> shopCategories(@PathVariable Long shopId) {
        return ApiResponse.success(null, consumerShopCatalogService.shopCategories(shopId));
    }

    @GetMapping("/shops/{shopId}/products")
    public ApiResponse<List<ShopProductData>> shopProducts(
            @PathVariable Long shopId,
            @RequestParam(required = false) Long categoryId
    ) {
        return ApiResponse.success(null, consumerShopCatalogService.shopProducts(shopId, categoryId));
    }

    @GetMapping("/shops/{shopId}/products/{productId}")
    public ApiResponse<ShopProductData> shopProductDetail(
            @PathVariable Long shopId,
            @PathVariable Long productId
    ) {
        return ApiResponse.success(null, consumerShopCatalogService.shopProductDetail(shopId, productId));
    }
}
