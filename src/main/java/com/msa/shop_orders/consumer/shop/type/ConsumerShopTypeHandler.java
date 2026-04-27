package com.msa.shop_orders.consumer.shop.type;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopCategoryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopDetailData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopSummaryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopTypeData;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;

import java.util.List;

public interface ConsumerShopTypeHandler {
    ShopTypeFamily family();
    List<ConsumerShopTypeData> shopTypes();
    List<ConsumerShopSummaryData> shops(Long shopTypeId, String search);
    ConsumerShopDetailData shopDetail(Long shopId);
    List<ConsumerShopCategoryData> shopCategories(Long shopId);
    List<ShopProductData> shopProducts(Long shopId, Long categoryId);
    ShopProductData shopProductDetail(Long shopId, Long productId);
}
