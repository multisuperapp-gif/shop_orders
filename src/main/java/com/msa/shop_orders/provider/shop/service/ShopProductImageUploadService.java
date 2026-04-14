package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopImageUploadData;
import org.springframework.web.multipart.MultipartFile;

public interface ShopProductImageUploadService {
    ShopImageUploadData upload(String assetType, MultipartFile file);
}
