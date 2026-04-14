package com.msa.shop_orders.provider.shop.controller;

import com.msa.shop_orders.common.api.ApiResponse;
import com.msa.shop_orders.provider.shop.dto.ShopImageUploadData;
import com.msa.shop_orders.provider.shop.service.ShopProductImageUploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/shops/product-images")
public class ShopProductImageController {
    private final ShopProductImageUploadService shopProductImageUploadService;

    public ShopProductImageController(ShopProductImageUploadService shopProductImageUploadService) {
        this.shopProductImageUploadService = shopProductImageUploadService;
    }

    @PostMapping("/upload")
    public ApiResponse<ShopImageUploadData> upload(
            @RequestParam String assetType,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success("Image uploaded successfully.", shopProductImageUploadService.upload(assetType, file));
    }
}
