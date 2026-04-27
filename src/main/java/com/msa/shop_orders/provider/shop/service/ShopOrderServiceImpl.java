package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.shoptype.ShopTypeFamilyResolver;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.type.order.ProviderShopOrderTypeRegistry;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ShopOrderServiceImpl implements ShopOrderService {
    private final ShopContextService shopContextService;
    private final ShopTypeFamilyResolver shopTypeFamilyResolver;
    private final ProviderShopOrderTypeRegistry providerShopOrderTypeRegistry;

    public ShopOrderServiceImpl(
            ShopContextService shopContextService,
            ShopTypeFamilyResolver shopTypeFamilyResolver,
            ProviderShopOrderTypeRegistry providerShopOrderTypeRegistry
    ) {
        this.shopContextService = shopContextService;
        this.shopTypeFamilyResolver = shopTypeFamilyResolver;
        this.providerShopOrderTypeRegistry = providerShopOrderTypeRegistry;
    }

    @Override
    public List<ShopOrderData> orders(String dateFilter, String status, LocalDate fromDate, LocalDate toDate) {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        return providerShopOrderTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shopEntity))
                .orders(shopEntity, dateFilter, status, fromDate, toDate);
    }

    @Override
    @Transactional
    public ShopOrderData updateOrderStatus(Long orderId, ShopOrderStatusUpdateRequest request) {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        return providerShopOrderTypeRegistry.resolve(shopTypeFamilyResolver.resolveFamily(shopEntity))
                .updateOrderStatus(shopEntity, orderId, request);
    }
}
