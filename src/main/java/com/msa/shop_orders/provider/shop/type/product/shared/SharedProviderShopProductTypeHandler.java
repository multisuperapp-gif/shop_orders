package com.msa.shop_orders.provider.shop.type.product.shared;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.provider.shop.dto.ShopProductStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.service.ShopCategoryViewService;
import com.msa.shop_orders.provider.shop.service.ShopProductWriteService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.provider.shop.type.product.ProviderShopProductTypeHandler;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SharedProviderShopProductTypeHandler implements ProviderShopProductTypeHandler {
    private final ShopCategoryViewService shopCategoryViewService;
    private final ShopProductWriteService shopProductWriteService;
    private final ShopRuntimeViewService shopRuntimeViewService;

    public SharedProviderShopProductTypeHandler(
            ShopCategoryViewService shopCategoryViewService,
            ShopProductWriteService shopProductWriteService,
            ShopRuntimeViewService shopRuntimeViewService
    ) {
        this.shopCategoryViewService = shopCategoryViewService;
        this.shopProductWriteService = shopProductWriteService;
        this.shopRuntimeViewService = shopRuntimeViewService;
    }

    @Override
    public ShopTypeFamily family() {
        return ShopTypeFamily.SHARED;
    }

    @Override
    public List<ShopProductData> products(ShopShellView shop, Long categoryId) {
        return shopRuntimeViewService.loadProducts(shop, categoryId);
    }

    @Override
    public ShopProductData createProduct(ShopShellView shop, ShopCreateProductRequest request) {
        ShopCategoryView category = requireMappedCategory(shop, request.categoryId());
        Long productId = shopProductWriteService.createProduct(shop, category, request).getId();
        return shopRuntimeViewService.loadProduct(shop, productId);
    }

    @Override
    public ShopProductData updateProduct(ShopShellView shop, Long productId, ShopCreateProductRequest request) {
        ShopCategoryView category = requireMappedCategory(shop, request.categoryId());
        shopProductWriteService.updateProduct(shop, productId, category, request);
        return shopRuntimeViewService.loadProduct(shop, productId);
    }

    @Override
    public ShopProductData duplicateProduct(ShopShellView shop, Long productId) {
        Long duplicateProductId = shopProductWriteService.duplicateProduct(shop, productId).getId();
        return shopRuntimeViewService.loadProduct(shop, duplicateProductId);
    }

    @Override
    public ShopProductData updateProductStatus(ShopShellView shop, Long productId, ShopProductStatusUpdateRequest request) {
        shopProductWriteService.updateProductStatus(productId, request != null && request.active());
        return shopRuntimeViewService.loadProduct(shop, productId);
    }

    private ShopCategoryView requireMappedCategory(ShopShellView shop, Long categoryId) {
        return shopCategoryViewService.findEnabledShopCategory(shop.getShopId(), shop.getShopTypeId(), categoryId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_ADDED", "Selected category is not added for this shop.", HttpStatus.BAD_REQUEST));
    }
}
