package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.consumer.cart.view.ConsumerCartView;
import com.msa.shop_orders.provider.shop.view.ShopInventoryMovementView;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.repository.ShopInventoryMovementViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShopInventoryMovementService {
    private final ShopInventoryMovementViewRepository shopInventoryMovementViewRepository;
    private final ShopOrderViewRepository shopOrderViewRepository;

    public ShopInventoryMovementService(
            ShopInventoryMovementViewRepository shopInventoryMovementViewRepository,
            ShopOrderViewRepository shopOrderViewRepository
    ) {
        this.shopInventoryMovementViewRepository = shopInventoryMovementViewRepository;
        this.shopOrderViewRepository = shopOrderViewRepository;
    }

    public void recordReserveAfterCommit(
            Long shopId,
            Long userId,
            Long orderId,
            String orderCode,
            List<ConsumerCartView.Item> items,
            String reason
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<ShopInventoryMovementView> documents = items.stream()
                .map(item -> reserveDocument(shopId, userId, orderId, orderCode, item, reason))
                .toList();
        runAfterCommit(documents);
    }

    private ShopInventoryMovementView reserveDocument(
            Long shopId,
            Long userId,
            Long orderId,
            String orderCode,
            ConsumerCartView.Item item,
            String reason
    ) {
        ShopInventoryMovementView document = new ShopInventoryMovementView();
        document.setShopId(shopId);
        document.setUserId(userId);
        document.setOrderId(orderId);
        document.setOrderCode(orderCode);
        document.setProductId(item.getProductId());
        document.setVariantId(item.getVariantId());
        document.setProductName(item.getProductName());
        document.setVariantName(item.getVariantName());
        document.setQuantity(item.getQuantity());
        document.setMovementType("RESERVE");
        document.setReferenceType("SHOP_ORDER");
        document.setReferenceId(orderId);
        document.setReferenceCode(orderCode);
        document.setSourceService("shop_orders");
        document.setReason(reason);
        document.setCreatedAt(LocalDateTime.now());
        return document;
    }

    private void runAfterCommit(List<ShopInventoryMovementView> documents) {
        if (documents.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    shopInventoryMovementViewRepository.saveAll(documents);
                }
            });
            return;
        }
        shopInventoryMovementViewRepository.saveAll(documents);
    }

    public void recordOrderMovement(
            Long orderId,
            String movementType,
            String reason,
            String sourceService
    ) {
        if (orderId == null || movementType == null || movementType.isBlank()) {
            return;
        }
        ShopOrderView documentOrder = shopOrderViewRepository.findById(orderId).orElse(null);
        if (documentOrder != null) {
            List<ShopInventoryMovementView> documents = (documentOrder.getItems() == null ? List.<ShopOrderView.Item>of() : documentOrder.getItems()).stream()
                    .map(item -> orderMovementDocument(documentOrder, item, movementType, reason, sourceService))
                    .toList();
            shopInventoryMovementViewRepository.saveAll(documents);
            return;
        }
        // Shop runtime source of truth is Mongo; if the order doc is missing, we do not rebuild from SQL.
    }

    private ShopInventoryMovementView orderMovementDocument(
            ShopOrderView order,
            ShopOrderView.Item item,
            String movementType,
            String reason,
            String sourceService
    ) {
        ShopInventoryMovementView document = new ShopInventoryMovementView();
        document.setShopId(order.getShopId());
        document.setUserId(order.getUserId());
        document.setOrderId(order.getOrderId());
        document.setOrderCode(order.getOrderCode());
        document.setProductId(item.getProductId());
        document.setVariantId(item.getVariantId());
        document.setProductName(item.getProductName());
        document.setVariantName(item.getVariantName());
        document.setQuantity(item.getQuantity());
        document.setMovementType(movementType);
        document.setReferenceType("SHOP_ORDER");
        document.setReferenceId(order.getOrderId());
        document.setReferenceCode(order.getOrderCode());
        document.setSourceService(sourceService);
        document.setReason(reason);
        document.setCreatedAt(LocalDateTime.now());
        return document;
    }
}
