package com.msa.shop_orders.provider.shop.type.order.shared;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.integration.bookingpayment.ShopOrdersBookingPaymentOrderClient;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentOrderDtos.NotifyShopOrderEventRequest;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentOrderDtos.UpdateShopOrderStatusRequest;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.service.ShopOrderStateWriteService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.provider.shop.type.order.ProviderShopOrderTypeHandler;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Component
public class SharedProviderShopOrderTypeHandler implements ProviderShopOrderTypeHandler {
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopOrderStateWriteService shopOrderStateWriteService;
    private final ShopOrdersBookingPaymentOrderClient bookingPaymentOrderClient;
    private final ShopOrderViewRepository shopOrderViewRepository;
    private final com.msa.shop_orders.internal.finance.order.service.InternalFinanceOrderSyncService internalFinanceOrderSyncService;
    private final com.msa.shop_orders.consumer.cart.service.ConsumerCartService consumerCartService;

    public SharedProviderShopOrderTypeHandler(
            ShopRuntimeViewService shopRuntimeViewService,
            ShopOrderStateWriteService shopOrderStateWriteService,
            ShopOrdersBookingPaymentOrderClient bookingPaymentOrderClient,
            ShopOrderViewRepository shopOrderViewRepository,
            com.msa.shop_orders.internal.finance.order.service.InternalFinanceOrderSyncService internalFinanceOrderSyncService,
            com.msa.shop_orders.consumer.cart.service.ConsumerCartService consumerCartService
    ) {
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopOrderStateWriteService = shopOrderStateWriteService;
        this.bookingPaymentOrderClient = bookingPaymentOrderClient;
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.internalFinanceOrderSyncService = internalFinanceOrderSyncService;
        this.consumerCartService = consumerCartService;
    }

    @Override
    public ShopTypeFamily family() {
        return ShopTypeFamily.SHARED;
    }

    @Override
    public List<ShopOrderData> orders(ShopShellView shop, String dateFilter, String status, LocalDate fromDate, LocalDate toDate) {
        return shopRuntimeViewService.loadOrders(shop, dateFilter, status, fromDate, toDate);
    }

    @Override
    public ShopOrderData updateOrderStatus(ShopShellView shop, Long orderId, ShopOrderStatusUpdateRequest request) {
        String newStatus = normalizeOrderStatus(request.newStatus());
        Long changedByUserId = shop.getOwnerUserId();
        ShopOrderView existing = requireShopOrder(shop, orderId);
        String oldStatus = normalizeOrderStatus(existing.getOrderStatus());

        if (newStatus.equalsIgnoreCase(oldStatus)) {
            return shopRuntimeViewService.loadOrder(shop, orderId);
        }
        if ("CANCELLED".equalsIgnoreCase(newStatus)) {
            boolean paid = "PAID".equalsIgnoreCase(
                    existing.getPaymentStatus() == null ? "" : existing.getPaymentStatus().trim());
            // Accept-first: an unpaid order (pending request or accepted-awaiting-
            // payment) has no payment to refund, and booking-payment has no finance
            // context for it — so reject it locally: release the reserved stock,
            // mark REJECTED (hidden from customer + shop, admin-only audit), and
            // notify the customer.
            if (!paid
                    && ("PENDING_ACCEPTANCE".equals(oldStatus) || "ACCEPTED".equals(oldStatus))) {
                internalFinanceOrderSyncService.releaseInventory(orderId);
                shopOrderStateWriteService.applyStateUpdate(
                        orderId,
                        new ShopOrderStateWriteService.OrderStateMutation(
                                "REJECTED",
                                "FAILED",
                                changedByUserId,
                                blankToNull(request.reason()),
                                null,
                                "SHOP"
                        )
                );
                notifyOrderEvent("ORDER_REJECTED", existing, blankToNull(request.reason()));
                return shopRuntimeViewService.loadOrder(shop, orderId);
            }
            // Paid order → booking-payment handles the refund + customer notice.
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
                orderId,
                new ShopOrderStateWriteService.OrderStateMutation(
                        newStatus,
                        null,
                        changedByUserId,
                        blankToNull(request.reason()),
                        null
                )
        );
        ShopOrderView notifyTarget = orderView != null ? orderView : existing;
        if ("ACCEPTED".equals(newStatus) && "PENDING_ACCEPTANCE".equals(oldStatus)) {
            // Accept-first: opens the customer's 5-minute payment window. The
            // shop has committed, so empty the customer's cart now (it was kept
            // intact until acceptance so a rejection could be retried).
            try {
                consumerCartService.clearCartForUser(existing.getUserId());
            } catch (Exception ignored) {
                // Cart clear is best-effort — never fail the acceptance on it.
            }
            notifyOrderEvent("ORDER_ACCEPTED", notifyTarget, null);
        } else {
            // Every other forward status change (preparing/dispatched/out for
            // delivery/delivered) notifies the customer with a sound.
            notifyOrderEvent(
                    "ORDER_STATUS",
                    notifyTarget,
                    "Your order is now " + humanizeOrderStatus(newStatus) + "."
            );
        }
        return orderView == null ? shopRuntimeViewService.loadOrder(shop, orderId) : shopRuntimeViewService.toShopOrderData(orderView);
    }

    private String humanizeOrderStatus(String status) {
        return switch (status == null ? "" : status.trim().toUpperCase(Locale.ROOT)) {
            case "PREPARING" -> "being prepared";
            case "DISPATCHED" -> "dispatched";
            case "OUT_FOR_DELIVERY" -> "out for delivery";
            case "DELIVERED" -> "delivered";
            default -> status == null ? "updated" : status.toLowerCase(Locale.ROOT);
        };
    }

    private void notifyOrderEvent(String event, ShopOrderView order, String message) {
        try {
            bookingPaymentOrderClient.notifyOrderEvent(new NotifyShopOrderEventRequest(
                    event,
                    order.getShopId(),
                    order.getUserId(),
                    order.getOrderId(),
                    order.getOrderCode(),
                    message
            ));
        } catch (Exception ignored) {
            // Best-effort push — never fail the status change on a notification error.
        }
    }

    private ShopOrderView requireShopOrder(ShopShellView shop, Long orderId) {
        return shopOrderViewRepository.findById(orderId)
                .filter(order -> shop.getShopId().equals(order.getShopId()))
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found for this shop.", HttpStatus.NOT_FOUND));
    }

    private void validateLocalTransition(String currentStatus, String newStatus) {
        String current = normalizeOrderStatus(currentStatus);
        switch (current) {
            // Accept-first: the shop accepts a pending request, which opens the
            // user's 5-minute payment window. (Reject is the CANCELLED branch.)
            case "PENDING_ACCEPTANCE" -> {
                if (!"ACCEPTED".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only ACCEPTED is allowed for a pending order request.", HttpStatus.BAD_REQUEST);
                }
            }
            // After ACCEPTED the user pays; once payment completes the shop starts
            // preparing. The shop cannot advance an ACCEPTED-but-unpaid order.
            case "PAYMENT_COMPLETED" -> {
                if (!"PREPARING".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only PREPARING is allowed after payment completion.", HttpStatus.BAD_REQUEST);
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
