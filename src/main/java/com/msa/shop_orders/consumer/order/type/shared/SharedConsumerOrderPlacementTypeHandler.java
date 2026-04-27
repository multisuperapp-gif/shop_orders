package com.msa.shop_orders.consumer.order.type.shared;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.consumer.cart.service.ConsumerCartService;
import com.msa.shop_orders.consumer.cart.view.ConsumerCartView;
import com.msa.shop_orders.consumer.checkout.dto.ConsumerCheckoutPreviewData;
import com.msa.shop_orders.consumer.checkout.dto.ConsumerCheckoutPreviewRequest;
import com.msa.shop_orders.consumer.checkout.service.ConsumerCheckoutService;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderRequest;
import com.msa.shop_orders.consumer.order.dto.ConsumerPlaceOrderResponse;
import com.msa.shop_orders.consumer.order.service.ShopOrderWriteService;
import com.msa.shop_orders.consumer.order.type.ConsumerOrderPlacementTypeHandler;
import com.msa.shop_orders.persistence.entity.PaymentEntity;
import com.msa.shop_orders.persistence.repository.PaymentRepository;
import com.msa.shop_orders.provider.shop.service.ShopInventoryMovementService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeSyncService;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.security.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class SharedConsumerOrderPlacementTypeHandler implements ConsumerOrderPlacementTypeHandler {
    private final CurrentUserService currentUserService;
    private final ConsumerCartService consumerCartService;
    private final ConsumerCheckoutService consumerCheckoutService;
    private final ShopOrderWriteService shopOrderWriteService;
    private final PaymentRepository paymentRepository;
    private final ShopInventoryMovementService shopInventoryMovementService;
    private final ShopRuntimeSyncService shopRuntimeSyncService;

    public SharedConsumerOrderPlacementTypeHandler(
            CurrentUserService currentUserService,
            ConsumerCartService consumerCartService,
            ConsumerCheckoutService consumerCheckoutService,
            ShopOrderWriteService shopOrderWriteService,
            PaymentRepository paymentRepository,
            ShopInventoryMovementService shopInventoryMovementService,
            ShopRuntimeSyncService shopRuntimeSyncService
    ) {
        this.currentUserService = currentUserService;
        this.consumerCartService = consumerCartService;
        this.consumerCheckoutService = consumerCheckoutService;
        this.shopOrderWriteService = shopOrderWriteService;
        this.paymentRepository = paymentRepository;
        this.shopInventoryMovementService = shopInventoryMovementService;
        this.shopRuntimeSyncService = shopRuntimeSyncService;
    }

    @Override
    public ShopTypeFamily family() {
        return ShopTypeFamily.SHARED;
    }

    @Override
    public ConsumerPlaceOrderResponse placeOrder(ConsumerPlaceOrderRequest request) {
        Long userId = currentUserService.currentUser().userId();
        ConsumerCheckoutPreviewData preview = consumerCheckoutService.preview(
                new ConsumerCheckoutPreviewRequest(request.addressId(), request.fulfillmentType())
        );
        if (!preview.canPlaceOrder()) {
            throw new BusinessException("ORDER_INVALID", "Order cannot be placed: " + String.join(", ", preview.issues()), HttpStatus.BAD_REQUEST);
        }
        ConsumerCartView cart = consumerCartService.currentCartView();
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessException("CART_EMPTY", "Active cart is empty.", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        String paymentCode = generateCode("PAY");
        ShopOrderWriteService.CreatedOrder createdOrder = shopOrderWriteService.createOrder(
                new ShopOrderWriteService.CreateOrderCommand(
                        userId,
                        preview.addressId(),
                        preview.fulfillmentType(),
                        preview.platformFee(),
                        preview.currencyCode(),
                        "PAYMENT_PENDING",
                        "PENDING",
                        "Shop order created",
                        now,
                        preview.addressLabel(),
                        preview.addressLine(),
                        paymentCode,
                        cart.getItems().stream()
                                .map(item -> new ShopOrderWriteService.CreateOrderItemCommand(
                                        item.getVariantId(),
                                        item.getQuantity(),
                                        item.getProductName(),
                                        item.getVariantName(),
                                        item.getImageFileId(),
                                        selectedOptionsJson(item)
                                ))
                                .toList()
                )
        );

        Long paymentId = insertPayment(createdOrder.orderId(), userId, preview.totalAmount(), preview.currencyCode(), paymentCode, now);
        ShopOrderView orderView = buildOrderView(createdOrder, now);

        shopInventoryMovementService.recordReserveAfterCommit(
                createdOrder.shopId(),
                userId,
                createdOrder.orderId(),
                createdOrder.orderCode(),
                cart.getItems(),
                "Order placed and stock reserved."
        );
        shopRuntimeSyncService.syncOrderAfterCommit(createdOrder.orderId(), orderView);
        consumerCartService.clearCurrentCart();

        return new ConsumerPlaceOrderResponse(
                createdOrder.orderId(),
                createdOrder.orderCode(),
                "PAYMENT_PENDING",
                "PENDING",
                paymentId,
                paymentCode,
                preview.totalAmount(),
                preview.currencyCode(),
                "PROCEED_TO_PAYMENT"
        );
    }

    private String selectedOptionsJson(ConsumerCartView.Item item) {
        if ((item.getSelectedOptionIds() == null || item.getSelectedOptionIds().isEmpty())
                && (item.getSelectedOptionNames() == null || item.getSelectedOptionNames().isEmpty())) {
            return "{\"optionIds\":[],\"optionNames\":[]}";
        }
        String ids = item.getSelectedOptionIds() == null ? "" : item.getSelectedOptionIds().stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        String names = item.getSelectedOptionNames() == null ? "" : item.getSelectedOptionNames().stream()
                .map(name -> "\"" + name.replace("\"", "\\\"") + "\"")
                .reduce((a, b) -> a + "," + b).orElse("");
        return "{\"optionIds\":[" + ids + "],\"optionNames\":[" + names + "]}";
    }

    private Long insertPayment(Long orderId, Long userId, BigDecimal amount, String currencyCode, String paymentCode, LocalDateTime initiatedAt) {
        PaymentEntity payment = new PaymentEntity();
        payment.setPaymentCode(paymentCode);
        payment.setPayableType("SHOP_ORDER");
        payment.setPayableId(orderId);
        payment.setPayerUserId(userId);
        payment.setPaymentStatus("INITIATED");
        payment.setAmount(amount);
        payment.setCurrencyCode(currencyCode);
        payment.setInitiatedAt(initiatedAt);
        return paymentRepository.save(payment).getId();
    }

    private ShopOrderView buildOrderView(ShopOrderWriteService.CreatedOrder order, LocalDateTime now) {
        ShopOrderView document = new ShopOrderView();
        document.setOrderId(order.orderId());
        document.setShopId(order.shopId());
        document.setUserId(order.userId());
        document.setOrderCode(order.orderCode());
        document.setShopName(order.shopName());
        document.setOrderStatus("PAYMENT_PENDING");
        document.setPaymentStatus("INITIATED");
        document.setPaymentCode(order.paymentCode());
        document.setFulfillmentType(order.fulfillmentType());
        document.setRefundPresent(false);
        document.setLatestRefundStatus(null);
        document.setRefund(null);
        document.setCreatedAt(order.createdAt());
        document.setUpdatedAt(now);
        document.setAddressLabel(order.addressLabel());
        document.setAddressLine(order.addressLine());
        document.setItemCount(order.items() == null ? 0 : order.items().stream()
                .map(ShopOrderWriteService.CreatedOrderItem::quantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        document.setSubtotalAmount(order.subtotalAmount());
        document.setTaxAmount(BigDecimal.ZERO);
        document.setItemsTotal(order.subtotalAmount());
        document.setDeliveryCharges(order.deliveryFeeAmount());
        document.setPlatformFee(order.platformFeeAmount());
        document.setDiscountAmount(BigDecimal.ZERO);
        document.setTotalOrderValue(order.totalAmount());
        document.setCurrencyCode(order.currencyCode());
        document.setCancellable(true);
        document.setItems(buildOrderItems(order));
        document.setTimeline(List.of(buildInitialTimelineEvent(order.createdAt())));
        return document;
    }

    private List<ShopOrderView.Item> buildOrderItems(ShopOrderWriteService.CreatedOrder order) {
        if (order.items() == null || order.items().isEmpty()) {
            return List.of();
        }
        List<ShopOrderView.Item> items = new java.util.ArrayList<>();
        for (ShopOrderWriteService.CreatedOrderItem item : order.items()) {
            ShopOrderView.Item document = new ShopOrderView.Item();
            document.setProductId(item.productId());
            document.setVariantId(item.variantId());
            document.setProductName(item.productNameSnapshot());
            document.setVariantName(item.variantNameSnapshot());
            document.setItemName(buildItemName(item.productNameSnapshot(), item.variantNameSnapshot()));
            document.setImageFileId(item.imageFileIdSnapshot());
            document.setQuantity(item.quantity());
            document.setUnitLabel(null);
            document.setUnitPrice(item.unitPrice());
            document.setLineTotal(item.lineTotal());
            items.add(document);
        }
        return items;
    }

    private ShopOrderView.TimelineEvent buildInitialTimelineEvent(LocalDateTime changedAt) {
        ShopOrderView.TimelineEvent event = new ShopOrderView.TimelineEvent();
        event.setOldStatus(null);
        event.setNewStatus("PAYMENT_PENDING");
        event.setReason(null);
        event.setChangedAt(changedAt);
        return event;
    }

    private String buildItemName(String productName, String variantName) {
        String resolvedProductName = productName == null || productName.isBlank() ? "Item" : productName;
        if (variantName == null || variantName.isBlank() || variantName.equalsIgnoreCase(resolvedProductName)) {
            return resolvedProductName;
        }
        return resolvedProductName + " (" + variantName + ")";
    }

    private static String generateCode(String prefix) {
        String raw = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return prefix + raw;
    }
}
