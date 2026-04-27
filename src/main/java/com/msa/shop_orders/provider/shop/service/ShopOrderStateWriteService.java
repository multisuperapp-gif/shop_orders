package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.OrderEntity;
import com.msa.shop_orders.persistence.entity.OrderStatusHistoryEntity;
import com.msa.shop_orders.persistence.repository.OrderRepository;
import com.msa.shop_orders.persistence.repository.OrderStatusHistoryRepository;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ShopOrderStateWriteService {
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopRuntimeSyncService shopRuntimeSyncService;

    public ShopOrderStateWriteService(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            ShopRuntimeViewService shopRuntimeViewService,
            ShopRuntimeSyncService shopRuntimeSyncService
    ) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopRuntimeSyncService = shopRuntimeSyncService;
    }

    public ShopOrderView applyStateUpdate(OrderEntity order, OrderStateMutation mutation) {
        if (order == null || mutation == null) {
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

        orderRepository.save(order);
        if (requestedStatus != null && !Objects.equals(oldStatus, requestedStatus)) {
            OrderStatusHistoryEntity history = new OrderStatusHistoryEntity();
            history.setOrderId(order.getId());
            history.setOldStatus(oldStatus);
            history.setNewStatus(requestedStatus);
            history.setChangedByUserId(mutation.changedByUserId());
            history.setReason(mutation.reason());
            history.setRefundPolicyApplied(mutation.refundPolicyApplied());
            history.setChangedAt(LocalDateTime.now());
            orderStatusHistoryRepository.save(history);
        }

        ShopOrderView orderView = shopRuntimeViewService.buildOrderViewById(order.getId());
        shopRuntimeSyncService.syncOrderAfterCommit(order.getId(), orderView);
        return orderView;
    }

    public record OrderStateMutation(
            String orderStatus,
            String paymentStatus,
            Long changedByUserId,
            String reason,
            String refundPolicyApplied
    ) {
    }
}
