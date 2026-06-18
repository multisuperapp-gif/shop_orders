package com.msa.shop_orders.consumer.order.service;

import com.msa.shop_orders.integration.bookingpayment.ShopOrdersBookingPaymentOrderClient;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentOrderDtos.NotifyShopOrderEventRequest;
import com.msa.shop_orders.internal.finance.order.service.InternalFinanceOrderSyncService;
import com.msa.shop_orders.provider.shop.service.ShopOrderStateWriteService;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Accept-first flow: once a shop accepts an order the customer has 5 minutes to
 * pay. Orders that stay ACCEPTED (unpaid) past that window are auto-cancelled and
 * their reserved stock is released. Mirrors the booking accepted-payment timeout.
 */
@Component
public class ShopOrderPaymentTimeoutScheduler {
    private static final Logger log = LoggerFactory.getLogger(ShopOrderPaymentTimeoutScheduler.class);
    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final long PAYMENT_WINDOW_MINUTES = 5;

    private final ShopOrderViewRepository shopOrderViewRepository;
    private final ShopOrdersBookingPaymentOrderClient bookingPaymentOrderClient;
    private final InternalFinanceOrderSyncService internalFinanceOrderSyncService;
    private final ShopOrderStateWriteService shopOrderStateWriteService;

    public ShopOrderPaymentTimeoutScheduler(
            ShopOrderViewRepository shopOrderViewRepository,
            ShopOrdersBookingPaymentOrderClient bookingPaymentOrderClient,
            InternalFinanceOrderSyncService internalFinanceOrderSyncService,
            ShopOrderStateWriteService shopOrderStateWriteService
    ) {
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.bookingPaymentOrderClient = bookingPaymentOrderClient;
        this.internalFinanceOrderSyncService = internalFinanceOrderSyncService;
        this.shopOrderStateWriteService = shopOrderStateWriteService;
    }

    // Runs every 15s so an order is cancelled right when its on-screen 5-minute
    // countdown reaches 0 (not up to a minute later). Cheap query (status +
    // timestamp). Covers both ACCEPTED (payment never started) and PAYMENT_PENDING
    // (payment sheet opened but abandoned) — both mean the window lapsed unpaid.
    // The window is anchored to createdAt (fixed at acceptance for accept-first
    // orders), NOT updatedAt — updatedAt is bumped by the ACCEPTED->PAYMENT_PENDING
    // write, which used to reset this window and let the order outlive the timer.
    @Scheduled(fixedDelay = 15_000L, initialDelay = 15_000L)
    public void cancelExpiredAcceptedOrders() {
        LocalDateTime cutoff = LocalDateTime.now(SHOP_ZONE).minusMinutes(PAYMENT_WINDOW_MINUTES);
        List<ShopOrderView> expired;
        try {
            expired = shopOrderViewRepository.findByOrderStatusInAndCreatedAtBefore(
                    List.of("ACCEPTED", "PAYMENT_PENDING"), cutoff);
        } catch (Exception exception) {
            log.warn("Failed to query expired accepted shop orders", exception);
            return;
        }
        for (ShopOrderView order : expired) {
            // Skip if it somehow got paid between the query and now.
            if (order.getPaymentStatus() != null
                    && "PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                continue;
            }
            try {
                // Unpaid timeout — auto-reject locally (no payment to refund):
                // release the reserved stock, mark REJECTED (treated like a
                // no-accept; hidden from customer + shop, admin-only), and notify.
                internalFinanceOrderSyncService.releaseInventory(order.getOrderId());
                shopOrderStateWriteService.applyStateUpdate(
                        order.getOrderId(),
                        new ShopOrderStateWriteService.OrderStateMutation(
                                "REJECTED",
                                "FAILED",
                                null,
                                "Payment time expired — order auto-rejected.",
                                null,
                                "SYSTEM"
                        )
                );
                notifyTimeout(order);
            } catch (Exception exception) {
                log.warn("Failed to auto-cancel expired accepted shop order {}", order.getOrderId(), exception);
            }
        }
    }

    private void notifyTimeout(ShopOrderView order) {
        try {
            bookingPaymentOrderClient.notifyOrderEvent(new NotifyShopOrderEventRequest(
                    "ORDER_TIMEOUT",
                    order.getShopId(),
                    order.getUserId(),
                    order.getOrderId(),
                    order.getOrderCode(),
                    null
            ));
        } catch (Exception ignored) {
            // Best-effort push.
        }
    }

    // Paid orders still open (ACCEPTED / PREPARING / DISPATCHED / OUT_FOR_DELIVERY)
    // that the shop never marked delivered. Same as labour/service: any such order
    // from a prior day is auto-marked DELIVERED once its day has ended (midnight).
    private static final List<String> AUTO_DELIVER_STATUSES =
            List.of("ACCEPTED", "PREPARING", "DISPATCHED", "OUT_FOR_DELIVERY");

    // Runs every 5 minutes. After midnight (shop zone) it sweeps the prior day's
    // unfinished paid orders and closes them out as delivered.
    @Scheduled(fixedDelay = 300_000L, initialDelay = 120_000L)
    public void autoDeliverOrdersAtEndOfDay() {
        LocalDateTime startOfToday = LocalDateTime.now(SHOP_ZONE).toLocalDate().atStartOfDay();
        List<ShopOrderView> openOrders;
        try {
            openOrders = shopOrderViewRepository.findByOrderStatusInAndCreatedAtBefore(
                    AUTO_DELIVER_STATUSES, startOfToday);
        } catch (Exception exception) {
            log.warn("Failed to query orders for end-of-day auto-delivery", exception);
            return;
        }
        for (ShopOrderView order : openOrders) {
            // Only close out orders the customer actually paid for.
            String payment = order.getPaymentStatus() == null ? "" : order.getPaymentStatus().trim().toUpperCase();
            boolean paid = "PAID".equals(payment) || "PAYMENT_COMPLETED".equals(payment) || "COMPLETED".equals(payment);
            if (!paid) {
                continue;
            }
            try {
                shopOrderStateWriteService.applyStateUpdate(
                        order.getOrderId(),
                        new ShopOrderStateWriteService.OrderStateMutation(
                                "DELIVERED",
                                null,
                                null,
                                "Auto-marked delivered — order was not closed by end of day.",
                                null,
                                "SYSTEM"
                        )
                );
                notifyAutoDelivered(order);
            } catch (Exception exception) {
                log.warn("Failed to auto-deliver shop order {}", order.getOrderId(), exception);
            }
        }
    }

    private void notifyAutoDelivered(ShopOrderView order) {
        try {
            bookingPaymentOrderClient.notifyOrderEvent(new NotifyShopOrderEventRequest(
                    "ORDER_STATUS",
                    order.getShopId(),
                    order.getUserId(),
                    order.getOrderId(),
                    order.getOrderCode(),
                    "Your order is now delivered."
            ));
        } catch (Exception ignored) {
            // Best-effort push.
        }
    }
}
