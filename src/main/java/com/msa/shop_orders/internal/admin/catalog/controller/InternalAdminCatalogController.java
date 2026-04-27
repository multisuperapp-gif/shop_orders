package com.msa.shop_orders.internal.admin.catalog.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.internal.admin.catalog.dto.AdminCatalogDtos;
import com.msa.shop_orders.internal.admin.catalog.service.InternalAdminCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/catalog")
public class InternalAdminCatalogController {
    private final InternalAdminCatalogService internalAdminCatalogService;

    public InternalAdminCatalogController(InternalAdminCatalogService internalAdminCatalogService) {
        this.internalAdminCatalogService = internalAdminCatalogService;
    }

    @GetMapping("/shops")
    public ApiResponse<List<AdminCatalogDtos.ShopSummaryData>> shops() {
        return ApiResponse.success(null, internalAdminCatalogService.shops());
    }

    @GetMapping("/shops/{shopId}")
    public ApiResponse<AdminCatalogDtos.ShopDetailData> shopDetail(@PathVariable Long shopId) {
        return ApiResponse.success(null, internalAdminCatalogService.shopDetail(shopId));
    }

    @GetMapping("/products")
    public ApiResponse<List<AdminCatalogDtos.ProductSummaryData>> products(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active
    ) {
        return ApiResponse.success(null, internalAdminCatalogService.products(shopId, categoryId, active));
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<AdminCatalogDtos.ProductDetailData> productDetail(@PathVariable Long productId) {
        return ApiResponse.success(null, internalAdminCatalogService.productDetail(productId));
    }

    @GetMapping("/product-categories")
    public ApiResponse<List<AdminCatalogDtos.ProductCategorySummaryData>> productCategories(@RequestParam Long shopId) {
        return ApiResponse.success(null, internalAdminCatalogService.productCategories(shopId));
    }

    @GetMapping("/metrics/low-stock-shops")
    public ApiResponse<Long> countShopsWithLowStockAlerts(
            @RequestParam(name = "shopId", required = false) List<Long> shopIds
    ) {
        return ApiResponse.success(null, internalAdminCatalogService.countShopsWithLowStockAlerts(shopIds));
    }

    @PatchMapping("/shops/{shopId}/operational-status")
    public ApiResponse<AdminCatalogDtos.ShopSummaryData> updateShopOperationalStatus(
            @PathVariable Long shopId,
            @RequestBody AdminCatalogDtos.ShopOperationalStatusUpdateRequest request
    ) {
        return ApiResponse.success(
                "Shop operational status updated successfully",
                internalAdminCatalogService.updateShopOperationalStatus(shopId, request)
        );
    }

    @PatchMapping("/products/{productId}/status")
    public ApiResponse<AdminCatalogDtos.ProductSummaryData> updateProductStatus(
            @PathVariable Long productId,
            @RequestBody AdminCatalogDtos.ProductActiveUpdateRequest request
    ) {
        return ApiResponse.success(
                Boolean.TRUE.equals(request.active()) ? "Product activated successfully" : "Product deactivated successfully",
                internalAdminCatalogService.updateProductStatus(productId, request)
        );
    }

    @PatchMapping("/products/{productId}/featured")
    public ApiResponse<AdminCatalogDtos.ProductSummaryData> updateProductFeatured(
            @PathVariable Long productId,
            @RequestBody AdminCatalogDtos.ProductFeaturedUpdateRequest request
    ) {
        return ApiResponse.success(
                Boolean.TRUE.equals(request.featured()) ? "Product featured successfully" : "Product unfeatured successfully",
                internalAdminCatalogService.updateProductFeatured(productId, request)
        );
    }

    @PatchMapping("/products/{productId}/promotion")
    public ApiResponse<AdminCatalogDtos.ProductDetailData> updatePromotionStatus(
            @PathVariable Long productId,
            @RequestBody AdminCatalogDtos.ProductPromotionStatusUpdateRequest request
    ) {
        return ApiResponse.success(
                "Product promotion status updated successfully",
                internalAdminCatalogService.updatePromotionStatus(productId, request)
        );
    }

    @PatchMapping("/products/{productId}/coupon")
    public ApiResponse<AdminCatalogDtos.ProductDetailData> updateCouponStatus(
            @PathVariable Long productId,
            @RequestBody AdminCatalogDtos.ProductCouponActiveUpdateRequest request
    ) {
        return ApiResponse.success(
                "Product coupon status updated successfully",
                internalAdminCatalogService.updateCouponStatus(productId, request)
        );
    }
}
