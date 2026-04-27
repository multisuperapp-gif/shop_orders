package com.msa.shop_orders.consumer.shop.type.fashion;

import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopCategoryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopDetailData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopSummaryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopTypeData;
import com.msa.shop_orders.consumer.shop.type.ConsumerShopTypeHandler;
import com.msa.shop_orders.consumer.shop.type.shared.SharedConsumerShopTypeHandler;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FashionConsumerShopTypeHandler implements ConsumerShopTypeHandler {
    private final SharedConsumerShopTypeHandler delegate;
    public FashionConsumerShopTypeHandler(SharedConsumerShopTypeHandler delegate) { this.delegate = delegate; }
    public ShopTypeFamily family() { return ShopTypeFamily.FASHION; }
    public List<ConsumerShopTypeData> shopTypes() { return delegate.shopTypes(); }
    public List<ConsumerShopSummaryData> shops(Long shopTypeId, String search) { return delegate.shops(shopTypeId, search); }
    public ConsumerShopDetailData shopDetail(Long shopId) { return delegate.shopDetail(shopId); }
    public List<ConsumerShopCategoryData> shopCategories(Long shopId) { return delegate.shopCategories(shopId); }
    public List<ShopProductData> shopProducts(Long shopId, Long categoryId) { return delegate.shopProducts(shopId, categoryId); }
    public ShopProductData shopProductDetail(Long shopId, Long productId) { return delegate.shopProductDetail(shopId, productId); }
}
