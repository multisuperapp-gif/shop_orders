package com.msa.shop_orders.internal.finance.order.service;

import com.msa.shop_orders.internal.finance.order.dto.InternalFinanceOrderDtos;
import com.msa.shop_orders.consumer.order.service.ShopOrderWriteService;
import com.msa.shop_orders.provider.shop.service.ShopOrderStateWriteService;
import com.msa.shop_orders.persistence.entity.InventoryEntity;
import com.msa.shop_orders.persistence.entity.OrderEntity;
import com.msa.shop_orders.persistence.entity.OrderItemEntity;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import com.msa.shop_orders.persistence.entity.ShopDeliveryRuleEntity;
import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import com.msa.shop_orders.persistence.entity.UserAddressEntity;
import com.msa.shop_orders.persistence.repository.InventoryRepository;
import com.msa.shop_orders.persistence.repository.OrderItemRepository;
import com.msa.shop_orders.persistence.repository.OrderRepository;
import com.msa.shop_orders.persistence.repository.ProductRepository;
import com.msa.shop_orders.persistence.repository.ProductVariantRepository;
import com.msa.shop_orders.persistence.repository.ShopDeliveryRuleRepository;
import com.msa.shop_orders.persistence.repository.ShopLocationRepository;
import com.msa.shop_orders.persistence.repository.UserAddressRepository;
import com.msa.shop_orders.provider.shop.service.ShopInventoryMovementService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeSyncService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.msa.shop_orders.common.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class InternalFinanceOrderSyncService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShopOrderWriteService shopOrderWriteService;
    private final ShopOrderStateWriteService shopOrderStateWriteService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final ShopLocationRepository shopLocationRepository;
    private final ShopDeliveryRuleRepository shopDeliveryRuleRepository;
    private final UserAddressRepository userAddressRepository;
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopRuntimeSyncService shopRuntimeSyncService;
    private final ShopInventoryMovementService shopInventoryMovementService;
    private final ShopOrderViewRepository shopOrderViewRepository;
    private final ShopShellViewRepository shopShellViewRepository;

    public InternalFinanceOrderSyncService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ShopOrderWriteService shopOrderWriteService,
            ShopOrderStateWriteService shopOrderStateWriteService,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            InventoryRepository inventoryRepository,
            ShopLocationRepository shopLocationRepository,
            ShopDeliveryRuleRepository shopDeliveryRuleRepository,
            UserAddressRepository userAddressRepository,
            ShopRuntimeViewService shopRuntimeViewService,
            ShopRuntimeSyncService shopRuntimeSyncService,
            ShopInventoryMovementService shopInventoryMovementService,
            ShopOrderViewRepository shopOrderViewRepository,
            ShopShellViewRepository shopShellViewRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.shopOrderWriteService = shopOrderWriteService;
        this.shopOrderStateWriteService = shopOrderStateWriteService;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
        this.shopLocationRepository = shopLocationRepository;
        this.shopDeliveryRuleRepository = shopDeliveryRuleRepository;
        this.userAddressRepository = userAddressRepository;
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopRuntimeSyncService = shopRuntimeSyncService;
        this.shopInventoryMovementService = shopInventoryMovementService;
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.shopShellViewRepository = shopShellViewRepository;
    }

    @Transactional
    public InternalFinanceOrderDtos.CreatedOrderData createOrder(InternalFinanceOrderDtos.CreateOrderRequest request) {
        if (request == null || request.userId() == null || request.addressId() == null) {
            throw new BusinessException("ORDER_INVALID", "Order request is incomplete.", HttpStatus.BAD_REQUEST);
        }
        validateItems(request.items());
        ShopOrderWriteService.CreatedOrder createdOrder = shopOrderWriteService.createOrder(
                new ShopOrderWriteService.CreateOrderCommand(
                        request.userId(),
                        request.addressId(),
                        request.fulfillmentType(),
                        BigDecimal.ZERO,
                        "INR",
                        "PAYMENT_PENDING",
                        "UNPAID",
                        "Shop order created",
                        LocalDateTime.now(),
                        null,
                        null,
                        null,
                        request.items().stream()
                                .map(item -> new ShopOrderWriteService.CreateOrderItemCommand(
                                        item.variantId(),
                                        item.quantity(),
                                        null,
                                        null,
                                        null,
                                        "{\"optionIds\":[],\"optionNames\":[]}"
                                ))
                                .toList()
                )
        );

        List<com.msa.shop_orders.consumer.cart.view.ConsumerCartView.Item> reserveItems = new ArrayList<>();
        for (ShopOrderWriteService.CreatedOrderItem item : createdOrder.items()) {
            com.msa.shop_orders.consumer.cart.view.ConsumerCartView.Item reserveItem =
                    new com.msa.shop_orders.consumer.cart.view.ConsumerCartView.Item();
            reserveItem.setProductId(item.productId());
            reserveItem.setVariantId(item.variantId());
            reserveItem.setProductName(item.productNameSnapshot());
            reserveItem.setVariantName(item.variantNameSnapshot());
            reserveItem.setQuantity(item.quantity());
            reserveItem.setUnitPrice(item.unitPrice());
            reserveItem.setLineTotal(item.lineTotal());
            reserveItems.add(reserveItem);
        }

        shopInventoryMovementService.recordReserveAfterCommit(
                createdOrder.shopId(),
                request.userId(),
                createdOrder.orderId(),
                createdOrder.orderCode(),
                reserveItems,
                "Order created and stock reserved."
        );
        ShopOrderView orderView = shopRuntimeViewService.buildOrderViewById(createdOrder.orderId());
        shopRuntimeSyncService.syncOrderAfterCommit(createdOrder.orderId(), orderView);

        return new InternalFinanceOrderDtos.CreatedOrderData(
                createdOrder.orderId(),
                createdOrder.orderCode(),
                createdOrder.shopId(),
                createdOrder.userId(),
                createdOrder.orderStatus(),
                createdOrder.paymentStatus(),
                createdOrder.subtotalAmount(),
                createdOrder.deliveryFeeAmount(),
                createdOrder.totalAmount(),
                createdOrder.platformFeeAmount(),
                createdOrder.currencyCode(),
                createdOrder.items().stream()
                        .map(item -> new InternalFinanceOrderDtos.CreatedOrderItemData(
                                item.productId(),
                                item.variantId(),
                                item.quantity(),
                                item.unitPrice(),
                                item.lineTotal()
                        ))
                        .toList()
        );
    }

    public void syncOrderRuntime(Long orderId, InternalFinanceOrderDtos.RuntimeSyncRequest request) {
        shopRuntimeViewService.syncOrderById(orderId);
        if (request == null || request.movementType() == null || request.movementType().isBlank()) {
            return;
        }
        shopInventoryMovementService.recordOrderMovement(
                orderId,
                request.movementType(),
                request.movementReason(),
                "booking_payment"
        );
    }

    public void recordOrderMovement(Long orderId, InternalFinanceOrderDtos.RuntimeSyncRequest request) {
        if (orderId == null || request == null || request.movementType() == null || request.movementType().isBlank()) {
            return;
        }
        shopInventoryMovementService.recordOrderMovement(
                orderId,
                request.movementType(),
                request.movementReason(),
                "booking_payment"
        );
    }

    public InternalFinanceOrderDtos.OrderFinanceContextData loadOrderContext(Long orderId) {
        if (orderId == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        }
        shopRuntimeViewService.syncOrderById(orderId);
        ShopOrderView order = shopOrderViewRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        return new InternalFinanceOrderDtos.OrderFinanceContextData(
                order.getOrderId(),
                order.getOrderCode(),
                order.getShopId(),
                order.getUserId(),
                order.getOrderStatus(),
                order.getPaymentStatus(),
                order.getSubtotalAmount(),
                order.getDeliveryCharges(),
                order.getTotalOrderValue(),
                order.getPlatformFee(),
                order.getCurrencyCode()
        );
    }

    public List<InternalFinanceOrderDtos.OrderItemData> loadOrderItems(Long orderId) {
        if (orderId == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        }
        return orderItemRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(item -> new InternalFinanceOrderDtos.OrderItemData(
                        item.getProductId(),
                        item.getVariantId(),
                        item.getQuantity()
                ))
                .toList();
    }

    public void updateOrderState(Long orderId, InternalFinanceOrderDtos.OrderStateUpdateRequest request) {
        if (orderId == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        }
        if (request == null) {
            return;
        }
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        shopOrderStateWriteService.applyStateUpdate(
                order,
                new ShopOrderStateWriteService.OrderStateMutation(
                        request.orderStatus(),
                        request.paymentStatus(),
                        request.changedByUserId(),
                        request.reason(),
                        request.refundPolicyApplied()
                )
        );
    }

    private void validateItems(List<InternalFinanceOrderDtos.CreateOrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("ORDER_INVALID", "At least one shop order item is required.", HttpStatus.BAD_REQUEST);
        }
        for (InternalFinanceOrderDtos.CreateOrderItemRequest item : items) {
            if (item == null || item.variantId() == null || item.quantity() == null || item.quantity() < 1) {
                throw new BusinessException("ORDER_INVALID", "Order item is incomplete.", HttpStatus.BAD_REQUEST);
            }
        }
    }
}
