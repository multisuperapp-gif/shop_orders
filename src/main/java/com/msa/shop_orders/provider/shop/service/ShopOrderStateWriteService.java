package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ShopOrderStateWriteService {
    private final ShopOrderViewRepository shopOrderViewRepository;

    public ShopOrderStateWriteService(
            ShopOrderViewRepository shopOrderViewRepository
    ) {
        this.shopOrderViewRepository = shopOrderViewRepository;
    }

    public ShopOrderView applyStateUpdate(Long orderId, OrderStateMutation mutation) {
        if (orderId == null || mutation == null) {
            return null;
        }
        ShopOrderView order = shopOrderViewRepository.findById(orderId).orElse(null);
        if (order == null) {
            return null;
        }

        String oldStatus = order.getOrderStatus();
        boolean changed = false;
        if (mutation.paymentStatus() != null && !mutation.paymentStatus().isBlank()) {
            order.setPaymentStatus(mutation.paymentStatus().trim().toUpperCase());
            changed = true;
        }

        String requestedStatus = mutation.orderStatus() == null || mutation.orderStatus().isBlank()
                ? null
                : mutation.orderStatus().trim().toUpperCase();
        if (requestedStatus != null && !Objects.equals(oldStatus, requestedStatus)) {
            order.setOrderStatus(requestedStatus);
            changed = true;
        }
        if (!changed) {
            return null;
        }

        order.setUpdatedAt(LocalDateTime.now());
        if (requestedStatus != null && !Objects.equals(oldStatus, requestedStatus)) {
            ShopOrderView.TimelineEvent history = new ShopOrderView.TimelineEvent();
            history.setOldStatus(oldStatus);
            history.setNewStatus(requestedStatus);
            history.setReason(mutation.reason());
            history.setChangedAt(LocalDateTime.now());
            List<ShopOrderView.TimelineEvent> timeline = new ArrayList<>(order.getTimeline() == null ? List.of() : order.getTimeline());
            timeline.add(history);
            order.setTimeline(timeline);
            if (("CANCELLED".equals(requestedStatus) || "REJECTED".equals(requestedStatus))
                    && mutation.cancelledBy() != null && !mutation.cancelledBy().isBlank()) {
                order.setCancelledBy(mutation.cancelledBy().trim().toUpperCase());
            }
        }
        shopOrderViewRepository.save(order);
        return order;
    }

    public record OrderStateMutation(
            String orderStatus,
            String paymentStatus,
            Long changedByUserId,
            String reason,
            String refundPolicyApplied,
            String cancelledBy
    ) {
        // Backwards-compatible constructor for callers that do not record the
        // cancelling actor (e.g. payment-status-only mutations).
        public OrderStateMutation(
                String orderStatus,
                String paymentStatus,
                Long changedByUserId,
                String reason,
                String refundPolicyApplied
        ) {
            this(orderStatus, paymentStatus, changedByUserId, reason, refundPolicyApplied, null);
        }
    }
}
