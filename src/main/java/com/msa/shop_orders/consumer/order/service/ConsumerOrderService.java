package com.msa.shop_orders.consumer.order.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderDetailData;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderItemData;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderTimelineEventData;
import com.msa.shop_orders.consumer.order.dto.ConsumerRefundSummaryData;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderSummaryData;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.security.CurrentUserService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConsumerOrderService {
    private final CurrentUserService currentUserService;
    private final ShopRuntimeViewService shopRuntimeViewService;

    public ConsumerOrderService(
            CurrentUserService currentUserService,
            ShopRuntimeViewService shopRuntimeViewService
    ) {
        this.currentUserService = currentUserService;
        this.shopRuntimeViewService = shopRuntimeViewService;
    }

    public List<ConsumerOrderSummaryData> orders() {
        Long userId = currentUserService.currentUser().userId();
        return shopRuntimeViewService.loadConsumerOrders(userId).stream()
                // REJECTED orders (shop rejected, or auto-rejected on no-accept
                // timeout) are hidden from the customer — admin-only for audit.
                .filter(order -> !"REJECTED".equalsIgnoreCase(order.getOrderStatus()))
                .map(this::toSummaryData)
                .toList();
    }

    public ConsumerOrderDetailData orderDetail(Long orderId) {
        Long userId = currentUserService.currentUser().userId();
        ShopOrderView document = shopRuntimeViewService.loadConsumerOrder(userId, orderId);
        if (document == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        }
        return toDetailData(document);
    }

    private ConsumerOrderSummaryData toSummaryData(ShopOrderView document) {
        ShopOrderView.Item primaryItem = document.getItems() == null || document.getItems().isEmpty()
                ? null
                : document.getItems().getFirst();
        return new ConsumerOrderSummaryData(
                document.getOrderId(),
                document.getOrderCode(),
                document.getShopId(),
                document.getShopName(),
                primaryItem == null ? "Order item" : primaryItem.getItemName(),
                primaryItem == null ? null : primaryItem.getImageFileId(),
                document.getItemCount(),
                document.getOrderStatus(),
                document.getPaymentStatus(),
                document.getTotalOrderValue(),
                document.getCurrencyCode(),
                Boolean.TRUE.equals(document.getCancellable()),
                document.isRefundPresent(),
                document.getLatestRefundStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                paymentSecondsRemaining(document)
        );
    }

    private ConsumerOrderDetailData toDetailData(ShopOrderView document) {
        return new ConsumerOrderDetailData(
                document.getOrderId(),
                document.getOrderCode(),
                document.getShopId(),
                document.getShopName(),
                document.getOrderStatus(),
                document.getPaymentStatus(),
                document.getPaymentCode(),
                document.getFulfillmentType(),
                document.getAddressLabel(),
                document.getAddressLine(),
                document.getSubtotalAmount(),
                document.getTaxAmount(),
                document.getDeliveryCharges(),
                document.getPlatformFee(),
                document.getDiscountAmount(),
                document.getTotalOrderValue(),
                document.getCurrencyCode(),
                Boolean.TRUE.equals(document.getCancellable()),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getItems() == null ? List.of() : document.getItems().stream()
                        .map(this::toItemData)
                        .toList(),
                document.getTimeline() == null ? List.of() : document.getTimeline().stream()
                        .map(this::toTimelineData)
                        .toList(),
                toRefundData(document.getRefund()),
                document.getCancelledBy(),
                document.getDeliveryLatitude(),
                document.getDeliveryLongitude(),
                // Only expose the agent position while the order is actually out
                // for delivery — stale coordinates are meaningless afterwards.
                isOutForDelivery(document) ? document.getDeliveryAgentLatitude() : null,
                isOutForDelivery(document) ? document.getDeliveryAgentLongitude() : null,
                isOutForDelivery(document) ? document.getDeliveryAgentLocationAt() : null,
                paymentSecondsRemaining(document)
        );
    }

    // Accept-first 5-minute payment window, anchored to the moment the shop
    // accepted (the ACCEPTED timeline event). Returned to the apps so the
    // countdown never restarts when the page is reopened.
    private static final long PAYMENT_WINDOW_SECONDS = 5 * 60;

    private Long paymentSecondsRemaining(ShopOrderView document) {
        String status = document.getOrderStatus() == null ? "" : document.getOrderStatus().trim().toUpperCase();
        if (!"ACCEPTED".equals(status) && !"PAYMENT_PENDING".equals(status)) {
            return null;
        }
        if ("PAID".equalsIgnoreCase(document.getPaymentStatus() == null ? "" : document.getPaymentStatus().trim())) {
            return null;
        }
        LocalDateTime acceptedAt = null;
        if (document.getTimeline() != null) {
            for (ShopOrderView.TimelineEvent event : document.getTimeline()) {
                String newStatus = event.getNewStatus() == null ? "" : event.getNewStatus().trim().toUpperCase();
                if ("ACCEPTED".equals(newStatus) && event.getChangedAt() != null) {
                    acceptedAt = event.getChangedAt();
                }
            }
        }
        if (acceptedAt == null) {
            acceptedAt = document.getUpdatedAt();
        }
        if (acceptedAt == null) {
            return PAYMENT_WINDOW_SECONDS;
        }
        long elapsed = Duration.between(acceptedAt, LocalDateTime.now()).getSeconds();
        return Math.max(0L, PAYMENT_WINDOW_SECONDS - elapsed);
    }

    private boolean isOutForDelivery(ShopOrderView document) {
        String status = document.getOrderStatus() == null ? "" : document.getOrderStatus().trim().toUpperCase();
        return "OUT_FOR_DELIVERY".equals(status) || "DISPATCHED".equals(status);
    }

    private ConsumerOrderItemData toItemData(ShopOrderView.Item item) {
        return new ConsumerOrderItemData(
                item.getProductId(),
                item.getVariantId(),
                item.getProductName(),
                item.getVariantName(),
                item.getImageFileId(),
                item.getQuantity(),
                item.getUnitLabel(),
                item.getUnitPrice(),
                item.getLineTotal()
        );
    }

    private ConsumerOrderTimelineEventData toTimelineData(ShopOrderView.TimelineEvent event) {
        return new ConsumerOrderTimelineEventData(
                event.getOldStatus(),
                event.getNewStatus(),
                event.getReason(),
                event.getChangedAt()
        );
    }

    private ConsumerRefundSummaryData toRefundData(ShopOrderView.Refund refund) {
        if (refund == null) {
            return null;
        }
        return new ConsumerRefundSummaryData(
                refund.getRefundCode(),
                refund.getRefundStatus(),
                refund.getRequestedAmount(),
                refund.getApprovedAmount(),
                refund.getReason(),
                refund.getInitiatedAt(),
                refund.getCompletedAt()
        );
    }
}
