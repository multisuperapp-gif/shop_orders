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

    // Runs every minute. Cheap query (status + timestamp) over shop orders.
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void cancelExpiredAcceptedOrders() {
        LocalDateTime cutoff = LocalDateTime.now(SHOP_ZONE).minusMinutes(PAYMENT_WINDOW_MINUTES);
        List<ShopOrderView> expired;
        try {
            expired = shopOrderViewRepository.findByOrderStatusAndUpdatedAtBefore("ACCEPTED", cutoff);
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
                                null
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
}
