package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.ShopTypeFamilyResolver;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderDeliveryLocationRequest;
import com.msa.shop_orders.provider.shop.dto.ShopOrderStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.type.order.ProviderShopOrderTypeRegistry;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShopOrderServiceImpl implements ShopOrderService {
    private final ShopContextService shopContextService;
    private final ShopTypeFamilyResolver shopTypeFamilyResolver;
    private final ProviderShopOrderTypeRegistry providerShopOrderTypeRegistry;
    private final ShopOrderViewRepository shopOrderViewRepository;

    public ShopOrderServiceImpl(
            ShopContextService shopContextService,
            ShopTypeFamilyResolver shopTypeFamilyResolver,
            ProviderShopOrderTypeRegistry providerShopOrderTypeRegistry,
            ShopOrderViewRepository shopOrderViewRepository
    ) {
        this.shopContextService = shopContextService;
        this.shopTypeFamilyResolver = shopTypeFamilyResolver;
        this.providerShopOrderTypeRegistry = providerShopOrderTypeRegistry;
        this.shopOrderViewRepository = shopOrderViewRepository;
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

    // Live delivery tracking: the business app streams the delivery agent's GPS
    // position here while the order is DISPATCHED / OUT_FOR_DELIVERY; the
    // customer app reads it back from the consumer order detail.
    @Override
    public void syncDeliveryAgentLocation(Long orderId, ShopOrderDeliveryLocationRequest request) {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        ShopOrderView order = shopOrderViewRepository.findById(orderId)
                .filter(view -> shopEntity.getShopId().equals(view.getShopId()))
                .orElseThrow(() -> new BusinessException(
                        "ORDER_NOT_FOUND", "Order not found for this shop.", HttpStatus.NOT_FOUND));
        String status = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim().toUpperCase();
        if (!"OUT_FOR_DELIVERY".equals(status) && !"DISPATCHED".equals(status)) {
            throw new BusinessException(
                    "ORDER_NOT_IN_DELIVERY",
                    "Live location can be shared only while the order is out for delivery.",
                    HttpStatus.BAD_REQUEST);
        }
        order.setDeliveryAgentLatitude(BigDecimal.valueOf(request.latitude()));
        order.setDeliveryAgentLongitude(BigDecimal.valueOf(request.longitude()));
        order.setDeliveryAgentLocationAt(LocalDateTime.now());
        // Mirror the agent's currently-selected route to the customer (kept as-is
        // when the request omits it, so a transient null never clears the route).
        if (request.routePolyline() != null && !request.routePolyline().isBlank()) {
            order.setDeliveryRoutePolyline(request.routePolyline());
        }
        shopOrderViewRepository.save(order);
    }
}
