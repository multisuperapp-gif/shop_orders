package com.msa.shop_orders.provider.shop.dto;

public record ShopInventoryAlertData(
        Long productId,
        Long categoryId,
        String categoryName,
        String itemName,
        String variantName,
        Integer quantityAvailable,
        Integer reorderLevel,
        String inventoryStatus,
        Long imageFileId
) {
}
