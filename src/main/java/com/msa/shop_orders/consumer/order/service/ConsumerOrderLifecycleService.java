package com.msa.shop_orders.consumer.order.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.integration.bookingpayment.ShopOrdersBookingPaymentOrderClient;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentApiResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentOrderDtos.CancelShopOrderRequest;
import com.msa.shop_orders.internal.finance.order.service.InternalFinanceOrderSyncService;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.service.ShopOrderStateWriteService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.security.CurrentUserService;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerOrderLifecycleService {
    private final CurrentUserService currentUserService;
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopOrdersBookingPaymentOrderClient shopOrdersBookingPaymentOrderClient;
    private final InternalFinanceOrderSyncService internalFinanceOrderSyncService;
    private final ShopOrderStateWriteService shopOrderStateWriteService;

    public ConsumerOrderLifecycleService(
            CurrentUserService currentUserService,
            ShopRuntimeViewService shopRuntimeViewService,
            ShopOrdersBookingPaymentOrderClient shopOrdersBookingPaymentOrderClient,
            InternalFinanceOrderSyncService internalFinanceOrderSyncService,
            ShopOrderStateWriteService shopOrderStateWriteService
    ) {
        this.currentUserService = currentUserService;
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopOrdersBookingPaymentOrderClient = shopOrdersBookingPaymentOrderClient;
        this.internalFinanceOrderSyncService = internalFinanceOrderSyncService;
        this.shopOrderStateWriteService = shopOrderStateWriteService;
    }

    @Transactional
    public void cancel(Long orderId, String reason) {
        Long userId = currentUserService.currentUser().userId();
        ShopOrderView order = shopRuntimeViewService.loadConsumerOrder(userId, orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        }

        String status = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim().toUpperCase();
        boolean paid = "PAID".equalsIgnoreCase(
                order.getPaymentStatus() == null ? "" : order.getPaymentStatus().trim());
        // Accept-first: an unpaid order (awaiting acceptance, or accepted but not
        // yet paid) has no payment to refund and booking-payment has no finance
        // context for it — routing it there fails (404) and leaves the order stuck
        // as PENDING_ACCEPTANCE, so it keeps showing/ringing in the shop app.
        // Cancel locally instead: release the reserved stock + mark CANCELLED. The
        // shop's 3s incoming-order poll then drops it from the live list/ring.
        if (!paid && ("PENDING_ACCEPTANCE".equals(status) || "ACCEPTED".equals(status))) {
            internalFinanceOrderSyncService.releaseInventory(orderId);
            // User cancel BEFORE the shop accepts is treated as a rejection
            // (REJECTED → hidden from customer + shop, admin-only). After the shop
            // has accepted it is a genuine customer cancellation (CANCELLED).
            String terminalStatus = "PENDING_ACCEPTANCE".equals(status) ? "REJECTED" : "CANCELLED";
            shopOrderStateWriteService.applyStateUpdate(
                    orderId,
                    new ShopOrderStateWriteService.OrderStateMutation(
                            terminalStatus,
                            "FAILED",
                            userId,
                            reason == null || reason.isBlank() ? "Cancelled by the customer." : reason,
                            null
                    )
            );
            return;
        }

        try {
            ShopOrdersBookingPaymentApiResponse<Void> response = shopOrdersBookingPaymentOrderClient.cancelByUser(
                    userId,
                    new CancelShopOrderRequest(orderId, userId, reason)
            );
            if (response == null || !response.success()) {
                throw new BusinessException(
                        "ORDER_CANCEL_FAILED",
                        response == null || response.message() == null || response.message().isBlank()
                                ? "Order cancellation failed."
                                : response.message(),
                        HttpStatus.BAD_REQUEST
                );
            }
        } catch (FeignException.NotFound exception) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        } catch (FeignException.BadRequest exception) {
            String content = exception.contentUTF8();
            throw new BusinessException(
                    "ORDER_CANCEL_FAILED",
                    content == null || content.isBlank() ? "Order cancellation failed." : content,
                    HttpStatus.BAD_REQUEST
            );
        } catch (FeignException exception) {
            throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "Order service is unavailable right now.", HttpStatus.BAD_REQUEST);
        }
    }
}
