package com.msa.shop_orders.provider.shop.dto;

public record ShopImageUploadData(
        Long fileId,
        String assetType,
        String mimeType,
        long fileSizeBytes,
        int width,
        int height,
        int ratioWidth,
        int ratioHeight,
        String originalFilename
) {
}
