package com.msa.shop_orders.user.shop.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.user.shop.service.UserShopProductQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/shop/products")
@ConditionalOnProperty(prefix = "app.mongodb", name = "enabled", havingValue = "true")
public class UserShopProductController {
    private final UserShopProductQueryService userShopProductQueryService;

    public UserShopProductController(UserShopProductQueryService userShopProductQueryService) {
        this.userShopProductQueryService = userShopProductQueryService;
    }

    @GetMapping
    public ApiResponse<List<ShopProductData>> products(
            @RequestParam Long shopId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search
    ) {
        return ApiResponse.success(null, userShopProductQueryService.products(shopId, categoryId, search));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ShopProductData> product(@PathVariable Long productId) {
        return ApiResponse.success(null, userShopProductQueryService.product(productId));
    }
}
