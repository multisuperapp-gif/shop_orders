package com.msa.shop_orders.consumer.order.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderDetailData;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderItemData;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderTimelineEventData;
import com.msa.shop_orders.consumer.order.dto.ConsumerRefundSummaryData;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderSummaryData;
import com.msa.shop_orders.consumer.order.dto.ConsumerOrderReviewRequest;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.security.CurrentUserService;
import com.msa.shop_orders.persistence.entity.UserEntity;
import com.msa.shop_orders.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerOrderService {
    private final CurrentUserService currentUserService;
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopOrderViewRepository shopOrderViewRepository;
    private final ShopShellViewRepository shopShellViewRepository;
    private final UserRepository userRepository;

    public ConsumerOrderService(
            CurrentUserService currentUserService,
            ShopRuntimeViewService shopRuntimeViewService,
            ShopOrderViewRepository shopOrderViewRepository,
            ShopShellViewRepository shopShellViewRepository,
            UserRepository userRepository
    ) {
        this.currentUserService = currentUserService;
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.shopShellViewRepository = shopShellViewRepository;
        this.userRepository = userRepository;
    }

    // Records a customer's 1-5 rating (+ optional comment) for a delivered order.
    @Transactional
    public void submitReview(Long orderId, ConsumerOrderReviewRequest request) {
        Long userId = currentUserService.currentUser().userId();
        ShopOrderView order = shopRuntimeViewService.loadConsumerOrder(userId, orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        }
        String status = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim().toUpperCase();
        if (!"DELIVERED".equals(status)) {
            throw new BusinessException(
                    "ORDER_NOT_DELIVERED",
                    "You can rate an order only after it is delivered.",
                    HttpStatus.BAD_REQUEST);
        }
        if (order.getRating() != null) {
            throw new BusinessException(
                    "ALREADY_REVIEWED", "You have already rated this order.", HttpStatus.BAD_REQUEST);
        }
        int rating = request == null || request.rating() == null ? 0 : request.rating();
        if (rating < 1 || rating > 5) {
            throw new BusinessException(
                    "INVALID_RATING", "Rating must be between 1 and 5.", HttpStatus.BAD_REQUEST);
        }
        order.setRating(rating);
        String comment = request.comment();
        order.setReviewComment(comment == null || comment.isBlank() ? null : comment.trim());
        order.setReviewedAt(LocalDateTime.now());
        shopOrderViewRepository.save(order);
        // Roll the new rating into the shop's overall average so the provider
        // dashboard shows it (previously left at "not rated yet").
        recomputeShopRating(order.getShopId());
    }

    // Recomputes a shop's aggregate rating from all its rated orders and writes
    // it back to the shop view that the dashboard + customer home read.
    private void recomputeShopRating(Long shopId) {
        if (shopId == null) {
            return;
        }
        List<Integer> ratings = shopOrderViewRepository.findByShopIdOrderByCreatedAtDesc(shopId).stream()
                .map(ShopOrderView::getRating)
                .filter(value -> value != null && value > 0)
                .toList();
        shopShellViewRepository.findById(shopId).ifPresent(shop -> {
            if (ratings.isEmpty()) {
                shop.setAvgRating(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                shop.setTotalReviews(0);
            } else {
                double average = ratings.stream().mapToInt(Integer::intValue).average().orElse(0);
                shop.setAvgRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
                shop.setTotalReviews(ratings.size());
            }
            shopShellViewRepository.save(shop);
        });
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

    // The customer's "Contact restaurant" button needs the shop phone. It is
    // captured on the order at creation, but older orders (or a shop owner whose
    // phone wasn't set then) have none — so fall back to resolving the owner's
    // phone live, which makes the button reliably appear on live orders.
    private String resolveShopPhone(ShopOrderView document) {
        String stored = document.getShopPhone();
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return shopShellViewRepository.findById(document.getShopId())
                .map(com.msa.shop_orders.provider.shop.view.ShopShellView::getOwnerUserId)
                .flatMap(ownerId -> userRepository.findById(ownerId))
                .map(UserEntity::getPhone)
                .filter(phone -> phone != null && !phone.isBlank())
                .orElse(stored);
    }

    private ConsumerOrderDetailData toDetailData(ShopOrderView document) {
        return new ConsumerOrderDetailData(
                document.getOrderId(),
                document.getOrderCode(),
                document.getShopId(),
                document.getShopName(),
                resolveShopPhone(document),
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
                isOutForDelivery(document) ? document.getDeliveryRoutePolyline() : null,
                paymentSecondsRemaining(document),
                // Customer only needs the completion OTP while it's out for delivery.
                isOutForDelivery(document) ? document.getDeliveryOtp() : null,
                document.getRating(),
                document.getReviewComment()
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
            // Fall back to the FIXED creation time, never updatedAt (which is bumped
            // by later writes and would reset/extend the window past the countdown).
            acceptedAt = document.getCreatedAt();
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
