package com.msa.shop_orders.provider.shop.type.order.shared;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.integration.bookingpayment.ShopOrdersBookingPaymentOrderClient;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentOrderDtos.UpdateShopOrderStatusRequest;
import com.msa.shop_orders.persistence.entity.OrderEntity;
import com.msa.shop_orders.persistence.repository.OrderRepository;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.service.ShopOrderStateWriteService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.provider.shop.type.order.ProviderShopOrderTypeHandler;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Component
public class SharedProviderShopOrderTypeHandler implements ProviderShopOrderTypeHandler {
    private final OrderRepository orderRepository;
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopOrderStateWriteService shopOrderStateWriteService;
    private final ShopOrdersBookingPaymentOrderClient bookingPaymentOrderClient;

    public SharedProviderShopOrderTypeHandler(
            OrderRepository orderRepository,
            ShopRuntimeViewService shopRuntimeViewService,
            ShopOrderStateWriteService shopOrderStateWriteService,
            ShopOrdersBookingPaymentOrderClient bookingPaymentOrderClient
    ) {
        this.orderRepository = orderRepository;
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopOrderStateWriteService = shopOrderStateWriteService;
        this.bookingPaymentOrderClient = bookingPaymentOrderClient;
    }

    @Override
    public ShopTypeFamily family() {
        return ShopTypeFamily.SHARED;
    }

    @Override
    public List<ShopOrderData> orders(ShopShellView shop, String dateFilter, String status, LocalDate fromDate, LocalDate toDate) {
        if (orderRepository.count() < 0) {
            return List.of();
        }
        return shopRuntimeViewService.loadOrders(shop, dateFilter, status, fromDate, toDate);
    }

    @Override
    public ShopOrderData updateOrderStatus(ShopShellView shop, Long orderId, ShopOrderStatusUpdateRequest request) {
        OrderEntity order = orderRepository.findByIdAndShopId(orderId, shop.getShopId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found for this shop.", HttpStatus.NOT_FOUND));
        String newStatus = normalizeOrderStatus(request.newStatus());
        Long changedByUserId = shop.getOwnerUserId();
        String oldStatus = normalizeOrderStatus(order.getOrderStatus());

        if (newStatus.equalsIgnoreCase(oldStatus)) {
            return shopRuntimeViewService.loadOrder(shop, orderId);
        }
        if ("CANCELLED".equalsIgnoreCase(newStatus)) {
            bookingPaymentOrderClient.updateStatus(new UpdateShopOrderStatusRequest(
                    orderId,
                    newStatus,
                    changedByUserId,
                    blankToNull(request.reason()),
                    null
            ));
            return shopRuntimeViewService.loadOrder(shop, orderId);
        }
        validateLocalTransition(oldStatus, newStatus);
        ShopOrderView orderView = shopOrderStateWriteService.applyStateUpdate(
                order,
                new ShopOrderStateWriteService.OrderStateMutation(
                        newStatus,
                        null,
                        changedByUserId,
                        blankToNull(request.reason()),
                        null
                )
        );
        return orderView == null ? shopRuntimeViewService.loadOrder(shop, orderId) : shopRuntimeViewService.toShopOrderData(orderView);
    }

    private void validateLocalTransition(String currentStatus, String newStatus) {
        String current = normalizeOrderStatus(currentStatus);
        switch (current) {
            case "PAYMENT_COMPLETED" -> {
                if (!"ACCEPTED".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only ACCEPTED is allowed after payment completion.", HttpStatus.BAD_REQUEST);
                }
            }
            case "ACCEPTED" -> {
                if (!"PREPARING".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only PREPARING is allowed after ACCEPTED.", HttpStatus.BAD_REQUEST);
                }
            }
            case "PREPARING" -> {
                if (!"DISPATCHED".equals(newStatus) && !"OUT_FOR_DELIVERY".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only DISPATCHED or OUT_FOR_DELIVERY is allowed after PREPARING.", HttpStatus.BAD_REQUEST);
                }
            }
            case "DISPATCHED" -> {
                if (!"OUT_FOR_DELIVERY".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only OUT_FOR_DELIVERY is allowed after DISPATCHED.", HttpStatus.BAD_REQUEST);
                }
            }
            case "OUT_FOR_DELIVERY" -> {
                if (!"DELIVERED".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only DELIVERED is allowed after OUT_FOR_DELIVERY.", HttpStatus.BAD_REQUEST);
                }
            }
            default -> throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Manual update is not allowed from the current order state.", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeOrderStatus(String newStatus) {
        return newStatus == null ? null : newStatus.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
