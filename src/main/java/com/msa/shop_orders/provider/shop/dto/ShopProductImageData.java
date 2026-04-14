package com.msa.shop_orders.provider.shop.dto;

public record ShopProductImageData(
        Long imageId,
        Long fileId,
        String imageRole,
        Long variantId,
        Integer sortOrder,
        boolean primaryImage
) {
}
