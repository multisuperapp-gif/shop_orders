package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.consumer.cart.view.ConsumerCartView;
import com.msa.shop_orders.persistence.entity.OrderEntity;
import com.msa.shop_orders.persistence.entity.OrderItemEntity;
import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import com.msa.shop_orders.persistence.repository.OrderItemRepository;
import com.msa.shop_orders.persistence.repository.OrderRepository;
import com.msa.shop_orders.persistence.repository.ProductVariantRepository;
import com.msa.shop_orders.provider.shop.view.ShopInventoryMovementView;
import com.msa.shop_orders.provider.shop.view.repository.ShopInventoryMovementViewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShopInventoryMovementService {
    private final ShopInventoryMovementViewRepository shopInventoryMovementViewRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final boolean viewStoreEnabled;

    public ShopInventoryMovementService(
            ShopInventoryMovementViewRepository shopInventoryMovementViewRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductVariantRepository productVariantRepository,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopInventoryMovementViewRepository = shopInventoryMovementViewRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public void recordReserveAfterCommit(
            Long shopId,
            Long userId,
            Long orderId,
            String orderCode,
            List<ConsumerCartView.Item> items,
            String reason
    ) {
        if (!viewStoreEnabled || items == null || items.isEmpty()) {
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
        if (!viewStoreEnabled || orderId == null || movementType == null || movementType.isBlank()) {
            return;
        }
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        List<OrderItemEntity> items = orderItemRepository.findByOrderIdIn(List.of(orderId));
        if (items.isEmpty()) {
            return;
        }
        Map<Long, Long> productIdByVariantId = loadProductIdsByVariantId(items);
        List<ShopInventoryMovementView> documents = items.stream()
                .map(item -> orderMovementDocument(order, item, productIdByVariantId, movementType, reason, sourceService))
                .toList();
        shopInventoryMovementViewRepository.saveAll(documents);
    }

    private Map<Long, Long> loadProductIdsByVariantId(Collection<OrderItemEntity> items) {
        return productVariantRepository.findAllById(items.stream()
                        .map(OrderItemEntity::getVariantId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, ProductVariantEntity::getProductId, (left, right) -> left, LinkedHashMap::new));
    }

    private ShopInventoryMovementView orderMovementDocument(
            OrderEntity order,
            OrderItemEntity item,
            Map<Long, Long> productIdByVariantId,
            String movementType,
            String reason,
            String sourceService
    ) {
        ShopInventoryMovementView document = new ShopInventoryMovementView();
        document.setShopId(order.getShopId());
        document.setUserId(order.getUserId());
        document.setOrderId(order.getId());
        document.setOrderCode(order.getOrderCode());
        document.setProductId(productIdByVariantId.get(item.getVariantId()));
        document.setVariantId(item.getVariantId());
        document.setQuantity(item.getQuantity());
        document.setMovementType(movementType);
        document.setReferenceType("SHOP_ORDER");
        document.setReferenceId(order.getId());
        document.setReferenceCode(order.getOrderCode());
        document.setSourceService(sourceService);
        document.setReason(reason);
        document.setCreatedAt(LocalDateTime.now());
        return document;
    }
}
