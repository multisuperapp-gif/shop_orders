package com.msa.shop_orders.consumer.storefront.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.consumer.storefront.dto.StorefrontDtos;
import com.msa.shop_orders.consumer.storefront.service.StorefrontCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop-orders/public")
public class StorefrontCatalogController {
    private final StorefrontCatalogService storefrontCatalogService;

    public StorefrontCatalogController(StorefrontCatalogService storefrontCatalogService) {
        this.storefrontCatalogService = storefrontCatalogService;
    }

    @GetMapping("/home/bootstrap")
    public ApiResponse<StorefrontDtos.HomeBootstrapData> homeBootstrap(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(null, storefrontCatalogService.homeBootstrap(latitude, longitude, page, size));
    }

    @GetMapping("/shop/types")
    public ApiResponse<List<StorefrontDtos.ShopTypeData>> shopTypes() {
        return ApiResponse.success(null, storefrontCatalogService.findShopTypes());
    }

    @GetMapping("/shop/categories")
    public ApiResponse<List<StorefrontDtos.ShopCategoryData>> shopCategories(
            @RequestParam(required = false) Long shopTypeId,
            @RequestParam(required = false) Long parentCategoryId
    ) {
        return ApiResponse.success(null, storefrontCatalogService.findCategories(shopTypeId, parentCategoryId));
    }

    @GetMapping("/shop/products")
    public ApiResponse<StorefrontDtos.PageResponse<StorefrontDtos.ShopProductCardData>> shopProducts(
            @RequestParam(required = false) Long shopTypeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(null, storefrontCatalogService.findProducts(
                shopTypeId,
                categoryId,
                search,
                latitude,
                longitude,
                page,
                size
        ));
    }

    @GetMapping("/shop/products/{productId}")
    public ApiResponse<StorefrontDtos.ProductDetailData> productDetail(
            @PathVariable Long productId,
            @RequestParam(required = false) Long variantId
    ) {
        return ApiResponse.success(null, storefrontCatalogService.findProductDetail(productId, variantId));
    }

    @GetMapping("/shop/types/{normalizedShopType}/landing")
    public ApiResponse<StorefrontDtos.ShopTypeLandingData> landing(
            @PathVariable String normalizedShopType,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(null, storefrontCatalogService.landing(normalizedShopType, latitude, longitude, page, size));
    }

    @GetMapping("/shop/types/{normalizedShopType}/categories")
    public ApiResponse<List<StorefrontDtos.ShopCategoryData>> typeCategories(
            @PathVariable String normalizedShopType,
            @RequestParam(required = false) Long parentCategoryId
    ) {
        return ApiResponse.success(null, storefrontCatalogService.categories(normalizedShopType, parentCategoryId));
    }

    @GetMapping("/shop/types/{normalizedShopType}/products")
    public ApiResponse<StorefrontDtos.PageResponse<StorefrontDtos.ShopProductCardData>> typeProducts(
            @PathVariable String normalizedShopType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(null, storefrontCatalogService.products(normalizedShopType, categoryId, search, page, size));
    }

    @GetMapping("/shop/types/{normalizedShopType}/shops")
    public ApiResponse<StorefrontDtos.PageResponse<StorefrontDtos.ShopSummaryData>> typeShops(
            @PathVariable String normalizedShopType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(null, storefrontCatalogService.shops(
                normalizedShopType,
                search,
                latitude,
                longitude,
                page,
                size
        ));
    }

    @GetMapping("/shop/types/{normalizedShopType}/shops/{shopId}")
    public ApiResponse<StorefrontDtos.ShopProfileData> shopProfile(
            @PathVariable String normalizedShopType,
            @PathVariable Long shopId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(null, storefrontCatalogService.shopProfile(
                normalizedShopType,
                shopId,
                categoryId,
                search,
                page,
                size
        ));
    }
}
