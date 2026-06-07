package com.msa.shop_orders.internal.finance.order.service;

import com.msa.shop_orders.internal.finance.order.dto.InternalFinanceOrderDtos;
import com.msa.shop_orders.consumer.order.service.ShopOrderWriteService;
import com.msa.shop_orders.provider.shop.service.ShopOrderStateWriteService;
import com.msa.shop_orders.provider.shop.service.ShopInventoryMovementService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeSyncService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopProductView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.msa.shop_orders.common.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class InternalFinanceOrderSyncService {
    private final ShopOrderWriteService shopOrderWriteService;
    private final ShopOrderStateWriteService shopOrderStateWriteService;
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopRuntimeSyncService shopRuntimeSyncService;
    private final ShopInventoryMovementService shopInventoryMovementService;
    private final ShopOrderViewRepository shopOrderViewRepository;
    private final ShopProductViewRepository shopProductViewRepository;

    public InternalFinanceOrderSyncService(
            ShopOrderWriteService shopOrderWriteService,
            ShopOrderStateWriteService shopOrderStateWriteService,
            ShopRuntimeViewService shopRuntimeViewService,
            ShopRuntimeSyncService shopRuntimeSyncService,
            ShopInventoryMovementService shopInventoryMovementService,
            ShopOrderViewRepository shopOrderViewRepository,
            ShopProductViewRepository shopProductViewRepository
    ) {
        this.shopOrderWriteService = shopOrderWriteService;
        this.shopOrderStateWriteService = shopOrderStateWriteService;
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopRuntimeSyncService = shopRuntimeSyncService;
        this.shopInventoryMovementService = shopInventoryMovementService;
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.shopProductViewRepository = shopProductViewRepository;
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
        ShopOrderView order = shopOrderViewRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        return (order.getItems() == null ? List.<ShopOrderView.Item>of() : order.getItems()).stream()
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
        if (shopOrderViewRepository.findById(orderId).isEmpty()) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        }
        shopOrderStateWriteService.applyStateUpdate(
                orderId,
                new ShopOrderStateWriteService.OrderStateMutation(
                        request.orderStatus(),
                        request.paymentStatus(),
                        request.changedByUserId(),
                        request.reason(),
                        request.refundPolicyApplied()
                )
        );
    }

    @Transactional
    public void reserveInventory(Long orderId) {
        mutateInventory(orderId, InventoryMutation.RESERVE);
    }

    @Transactional
    public void releaseInventory(Long orderId) {
        mutateInventory(orderId, InventoryMutation.RELEASE);
    }

    @Transactional
    public void consumeInventory(Long orderId) {
        mutateInventory(orderId, InventoryMutation.CONSUME);
    }

    @Transactional
    public void restockInventory(Long orderId) {
        mutateInventory(orderId, InventoryMutation.RESTOCK);
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

    private void mutateInventory(Long orderId, InventoryMutation mutation) {
        ShopOrderView order = shopOrderViewRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        List<ShopOrderView.Item> items = order.getItems() == null ? List.of() : order.getItems();
        if (items.isEmpty()) {
            return;
        }
        List<Long> variantIds = items.stream()
                .map(ShopOrderView.Item::getVariantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ProductVariantSelection> selectionsByVariantId = new LinkedHashMap<>();
        for (ShopProductView product : shopProductViewRepository.findByVariantsVariantIdIn(variantIds)) {
            for (ShopProductView.Variant variant : safeVariants(product)) {
                if (variant.getVariantId() != null && variantIds.contains(variant.getVariantId())) {
                    selectionsByVariantId.put(variant.getVariantId(), new ProductVariantSelection(product, variant));
                }
            }
        }
        if (selectionsByVariantId.size() != variantIds.size()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "One or more ordered variants are no longer available.", HttpStatus.NOT_FOUND);
        }
        for (ShopOrderView.Item item : items) {
            if (item.getVariantId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BusinessException("ORDER_INVALID", "Order item is incomplete.", HttpStatus.BAD_REQUEST);
            }
            ProductVariantSelection selection = selectionsByVariantId.get(item.getVariantId());
            validateInventoryMutation(selection.product(), selection.variant(), item.getQuantity(), mutation);
        }

        Map<Long, ShopProductView> mutatedProductsById = new LinkedHashMap<>();
        for (ShopOrderView.Item item : items) {
            ProductVariantSelection selection = selectionsByVariantId.get(item.getVariantId());
            applyInventoryMutation(selection.variant(), item.getQuantity(), mutation);
            updateProductSummaryFromVariants(selection.product());
            mutatedProductsById.put(selection.product().getProductId(), selection.product());
        }
        if (!mutatedProductsById.isEmpty()) {
            shopProductViewRepository.saveAll(mutatedProductsById.values());
        }
    }

    private void validateInventoryMutation(
            ShopProductView product,
            ShopProductView.Variant variant,
            Integer quantity,
            InventoryMutation mutation
    ) {
        if (product == null || variant == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Selected product is no longer available.", HttpStatus.NOT_FOUND);
        }
        int available = defaultInteger(variant.getQuantityAvailable());
        int reserved = defaultInteger(variant.getReservedQuantity());
        int availableToReserve = available - reserved;
        switch (mutation) {
            case RESERVE -> {
                if (!product.isActive() || !variant.isActive()) {
                    throw new BusinessException("PRODUCT_INACTIVE", "Selected product is inactive.", HttpStatus.BAD_REQUEST);
                }
                if (availableToReserve < quantity) {
                    throw new BusinessException("OUT_OF_STOCK", "Requested quantity is not available for variant " + variant.getVariantId() + ".", HttpStatus.BAD_REQUEST);
                }
                String inventoryStatus = variant.getInventoryStatus();
                if (!"IN_STOCK".equalsIgnoreCase(inventoryStatus) && !"LOW_STOCK".equalsIgnoreCase(inventoryStatus)) {
                    throw new BusinessException("OUT_OF_STOCK", "Selected item is out of stock.", HttpStatus.BAD_REQUEST);
                }
            }
            case CONSUME -> {
                if (reserved < quantity || available < quantity) {
                    throw new BusinessException("OUT_OF_STOCK", "Reserved inventory could not be committed for variant " + variant.getVariantId() + ".", HttpStatus.BAD_REQUEST);
                }
            }
            case RELEASE, RESTOCK -> {
                // no extra validation needed
            }
        }
    }

    private void applyInventoryMutation(ShopProductView.Variant variant, Integer quantity, InventoryMutation mutation) {
        int available = defaultInteger(variant.getQuantityAvailable());
        int reserved = defaultInteger(variant.getReservedQuantity());
        switch (mutation) {
            case RESERVE -> variant.setReservedQuantity(reserved + quantity);
            case RELEASE -> variant.setReservedQuantity(Math.max(0, reserved - quantity));
            case CONSUME -> {
                variant.setQuantityAvailable(Math.max(0, available - quantity));
                variant.setReservedQuantity(Math.max(0, reserved - quantity));
            }
            case RESTOCK -> variant.setQuantityAvailable(available + quantity);
        }
    }

    private void updateProductSummaryFromVariants(ShopProductView product) {
        ShopProductView.Variant primary = safeVariants(product).stream()
                .filter(ShopProductView.Variant::isDefaultVariant)
                .findFirst()
                .orElseGet(() -> safeVariants(product).stream().findFirst().orElse(null));
        if (primary == null) {
            return;
        }
        product.setVariantName(primary.getVariantName());
        product.setUnitValue(primary.getUnitValue());
        product.setUnitType(primary.getUnitType());
        product.setWeightInGrams(primary.getWeightInGrams());
        product.setMrp(primary.getMrp());
        product.setSellingPrice(primary.getSellingPrice());
        product.setQuantityAvailable(primary.getQuantityAvailable());
        product.setReservedQuantity(primary.getReservedQuantity());
        product.setReorderLevel(primary.getReorderLevel());
        product.setInventoryStatus(resolveInventoryStatus(
                primary.getQuantityAvailable(),
                primary.getReservedQuantity(),
                primary.getReorderLevel(),
                product.isActive() && primary.isActive()
        ));
        primary.setInventoryStatus(product.getInventoryStatus());
        product.setUpdatedAt(LocalDateTime.now());
    }

    private String resolveInventoryStatus(Integer quantityAvailable, Integer reservedQuantity, Integer reorderLevel, boolean active) {
        if (!active) {
            return "DISCONTINUED";
        }
        int available = defaultInteger(quantityAvailable) - defaultInteger(reservedQuantity);
        if (available <= 0) {
            return "OUT_OF_STOCK";
        }
        if (reorderLevel != null && available <= reorderLevel) {
            return "LOW_STOCK";
        }
        return "IN_STOCK";
    }

    private List<ShopProductView.Variant> safeVariants(ShopProductView product) {
        return product.getVariants() == null ? List.of() : product.getVariants();
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private record ProductVariantSelection(ShopProductView product, ShopProductView.Variant variant) {
    }

    private enum InventoryMutation {
        RESERVE,
        RELEASE,
        CONSUME,
        RESTOCK
    }
}
