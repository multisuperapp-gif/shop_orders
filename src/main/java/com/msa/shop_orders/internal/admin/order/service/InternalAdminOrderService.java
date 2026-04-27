package com.msa.shop_orders.internal.admin.order.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.internal.admin.order.dto.AdminOrderDtos;
import com.msa.shop_orders.integration.bookingpayment.ShopOrdersBookingPaymentOrderClient;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentApiResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentOrderDtos.UpdateShopOrderStatusRequest;
import com.msa.shop_orders.persistence.entity.OrderEntity;
import com.msa.shop_orders.persistence.entity.OrderItemEntity;
import com.msa.shop_orders.persistence.entity.ReturnRequestEntity;
import com.msa.shop_orders.persistence.entity.UserAddressEntity;
import com.msa.shop_orders.persistence.entity.UserEntity;
import com.msa.shop_orders.persistence.repository.OrderItemRepository;
import com.msa.shop_orders.persistence.repository.OrderRepository;
import com.msa.shop_orders.persistence.repository.OrderStatusHistoryRepository;
import com.msa.shop_orders.persistence.repository.ReturnRequestRepository;
import com.msa.shop_orders.persistence.repository.UserAddressRepository;
import com.msa.shop_orders.persistence.repository.UserRepository;
import com.msa.shop_orders.provider.shop.service.ShopOrderStateWriteService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalAdminOrderService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ShopShellViewRepository shopShellViewRepository;
    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;
    private final ShopOrdersBookingPaymentOrderClient shopOrdersBookingPaymentOrderClient;
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopOrderStateWriteService shopOrderStateWriteService;

    public InternalAdminOrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            ReturnRequestRepository returnRequestRepository,
            ShopShellViewRepository shopShellViewRepository,
            UserAddressRepository userAddressRepository,
            UserRepository userRepository,
            ShopOrdersBookingPaymentOrderClient shopOrdersBookingPaymentOrderClient,
            ShopRuntimeViewService shopRuntimeViewService,
            ShopOrderStateWriteService shopOrderStateWriteService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.shopShellViewRepository = shopShellViewRepository;
        this.userAddressRepository = userAddressRepository;
        this.userRepository = userRepository;
        this.shopOrdersBookingPaymentOrderClient = shopOrdersBookingPaymentOrderClient;
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopOrderStateWriteService = shopOrderStateWriteService;
    }

    public List<AdminOrderDtos.OrderSummaryData> orders(
            String status,
            String viewType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<OrderEntity> filtered = filteredOrders(status, viewType, dateFrom, dateTo);
        Map<Long, ShopShellView> shopsById = shopShellViewRepository.findAllById(
                filtered.stream().map(OrderEntity::getShopId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(ShopShellView::getShopId, Function.identity()));
        Map<Long, UserEntity> usersById = userRepository.findAllById(
                filtered.stream().map(OrderEntity::getUserId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return filtered.stream()
                .map(order -> {
                    UserEntity user = usersById.get(order.getUserId());
                    ShopShellView shop = shopsById.get(order.getShopId());
                    return new AdminOrderDtos.OrderSummaryData(
                            order.getId(),
                            order.getAddressId(),
                            order.getShopId(),
                            order.getOrderCode(),
                            order.getOrderStatus(),
                            user == null ? null : user.getPublicUserId(),
                            user == null ? null : user.getPhone(),
                            shop == null ? null : shop.getShopName(),
                            order.getTotalAmount(),
                            order.getCreatedAt()
                    );
                })
                .toList();
    }

    public AdminOrderDtos.OrderOperationsSummaryData orderSummary(
            String status,
            String viewType,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<OrderEntity> filtered = filteredOrders(status, viewType, dateFrom, dateTo);
        long totalLiveOrders = filtered.stream().filter(this::isLiveOrder).count();
        long inProgressOrders = filtered.stream().filter(this::isInProgressOrder).count();
        long completedOrders = filtered.stream().filter(this::isCompletedOrder).count();
        long outForDeliveryOrders = filtered.stream().filter(this::isOutForDeliveryOrder).count();
        return new AdminOrderDtos.OrderOperationsSummaryData(totalLiveOrders, inProgressOrders, completedOrders, outForDeliveryOrders);
    }

    public AdminOrderDtos.OrderDetailData orderDetail(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        UserEntity user = userRepository.findById(order.getUserId()).orElse(null);
        ShopShellView shop = shopShellViewRepository.findById(order.getShopId()).orElse(null);

        List<AdminOrderDtos.OrderItemData> items = orderItemRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(this::toOrderItemData)
                .toList();
        List<AdminOrderDtos.ReturnRequestData> returns = returnRequestRepository.findByOrderIdOrderByRequestedAtDescIdDesc(orderId).stream()
                .map(request -> new AdminOrderDtos.ReturnRequestData(
                        request.getOrderItemId(),
                        request.getReason(),
                        request.getStatus(),
                        request.getRequestedAt()
                ))
                .toList();
        CancellationExtras cancellationExtras = orderStatusHistoryRepository
                .findFirstByOrderIdAndNewStatusOrderByChangedAtDescIdDesc(orderId, "CANCELLED")
                .map(history -> new CancellationExtras(history.getReason(), history.getRefundPolicyApplied()))
                .orElse(null);
        UserAddressEntity address = userAddressRepository.findById(order.getAddressId()).orElse(null);

        return new AdminOrderDtos.OrderDetailData(
                order.getId(),
                order.getAddressId(),
                order.getOrderCode(),
                order.getOrderStatus(),
                user == null ? null : user.getPublicUserId(),
                user == null ? null : user.getPhone(),
                order.getShopId(),
                shop == null ? null : shop.getShopName(),
                formatAddress(address),
                order.getSubtotalAmount(),
                order.getTaxAmount(),
                order.getDeliveryFeeAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getCurrencyCode(),
                order.getCreatedAt(),
                cancellationExtras == null ? null : cancellationExtras.reason(),
                cancellationExtras == null ? null : cancellationExtras.refundPolicyApplied(),
                items,
                returns
        );
    }

    public AdminOrderDtos.OrderDetailData cancelOrder(Long orderId, AdminOrderDtos.CancelOrderRequest request) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        if ("CANCELLED".equalsIgnoreCase(order.getOrderStatus())) {
            return orderDetail(orderId);
        }
        try {
            ShopOrdersBookingPaymentApiResponse<Object> response = shopOrdersBookingPaymentOrderClient.updateStatus(
                    new UpdateShopOrderStatusRequest(
                            orderId,
                            "CANCELLED",
                            request == null ? null : request.changedByUserId(),
                            request == null ? null : request.reason(),
                            request == null ? null : request.refundPolicyApplied()
                    )
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
        return orderDetail(orderId);
    }

    @Transactional
    public AdminOrderDtos.OrderDetailData updateOrderStatus(Long orderId, AdminOrderDtos.UpdateOrderStatusRequest request) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
        String newStatus = normalizeOrderStatus(request == null ? null : request.newStatus());
        String oldStatus = normalizeOrderStatus(order.getOrderStatus());

        if (newStatus == null) {
            throw new BusinessException("ORDER_STATUS_REQUIRED", "New order status is required.", HttpStatus.BAD_REQUEST);
        }
        if (newStatus.equals(oldStatus)) {
            return orderDetail(orderId);
        }
        if ("CANCELLED".equals(newStatus)) {
            throw new BusinessException("USE_CANCEL_ENDPOINT", "Use the cancel endpoint for order cancellation.", HttpStatus.BAD_REQUEST);
        }

        validateLocalTransition(oldStatus, newStatus);
        shopOrderStateWriteService.applyStateUpdate(
                order,
                new ShopOrderStateWriteService.OrderStateMutation(
                        newStatus,
                        null,
                        request.changedByUserId(),
                        blankToNull(request.reason()),
                        null
                )
        );
        return orderDetail(orderId);
    }

    private List<OrderEntity> filteredOrders(String status, String viewType, LocalDate dateFrom, LocalDate dateTo) {
        return orderRepository.findAll().stream()
                .filter(order -> status == null || status.isBlank() || status.equalsIgnoreCase(order.getOrderStatus()))
                .filter(order -> matchesViewType(order, viewType))
                .filter(order -> matchesDateRange(order, dateFrom, dateTo))
                .toList();
    }

    private void validateLocalTransition(String currentStatus, String newStatus) {
        String current = normalizeOrderStatus(currentStatus);
        switch (current) {
            case "PAYMENT_COMPLETED" -> {
                if (!"ACCEPTED".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only ACCEPTED is allowed after payment completion.", HttpStatus.BAD_REQUEST);
                }
            }
            case "ACCEPTED" -> {
                if (!"PREPARING".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only PREPARING is allowed after ACCEPTED.", HttpStatus.BAD_REQUEST);
                }
            }
            case "PREPARING" -> {
                if (!"DISPATCHED".equals(newStatus) && !"OUT_FOR_DELIVERY".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only DISPATCHED or OUT_FOR_DELIVERY is allowed after PREPARING.", HttpStatus.BAD_REQUEST);
                }
            }
            case "DISPATCHED" -> {
                if (!"OUT_FOR_DELIVERY".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only OUT_FOR_DELIVERY is allowed after DISPATCHED.", HttpStatus.BAD_REQUEST);
                }
            }
            case "OUT_FOR_DELIVERY" -> {
                if (!"DELIVERED".equals(newStatus)) {
                    throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Only DELIVERED is allowed after OUT_FOR_DELIVERY.", HttpStatus.BAD_REQUEST);
                }
            }
            default -> throw new BusinessException("INVALID_ORDER_STATUS_TRANSITION", "Manual update is not allowed from the current order state.", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeOrderStatus(String status) {
        return status == null ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean matchesViewType(OrderEntity order, String viewType) {
        if (viewType == null || viewType.isBlank()) {
            return true;
        }
        return switch (viewType.trim().toUpperCase()) {
            case "LIVE" -> isLiveOrder(order);
            case "HISTORY" -> isCompletedOrder(order);
            default -> true;
        };
    }

    private boolean matchesDateRange(OrderEntity order, LocalDate dateFrom, LocalDate dateTo) {
        if (order.getCreatedAt() == null) {
            return false;
        }
        LocalDate orderDate = order.getCreatedAt().atZone(BUSINESS_ZONE).toLocalDate();
        if (dateFrom != null && orderDate.isBefore(dateFrom)) {
            return false;
        }
        return dateTo == null || !orderDate.isAfter(dateTo);
    }

    private boolean isLiveOrder(OrderEntity order) {
        String normalized = normalizeStatus(order.getOrderStatus());
        return !isCompletedStatus(normalized) && !"CANCELLED".equals(normalized);
    }

    private boolean isInProgressOrder(OrderEntity order) {
        return switch (normalizeStatus(order.getOrderStatus())) {
            case "ACCEPTED", "PAYMENT_PENDING", "PAYMENT_COMPLETED", "PREPARING", "DISPATCHED" -> true;
            default -> false;
        };
    }

    private boolean isCompletedOrder(OrderEntity order) {
        return isCompletedStatus(order.getOrderStatus());
    }

    private AdminOrderDtos.OrderItemData toOrderItemData(OrderItemEntity item) {
        return new AdminOrderDtos.OrderItemData(
                item.getVariantId(),
                item.getProductNameSnapshot(),
                item.getVariantNameSnapshot(),
                item.getQuantity(),
                item.getUnitPriceSnapshot(),
                item.getTaxSnapshot(),
                item.getLineTotal()
        );
    }

    private String formatAddress(UserAddressEntity address) {
        if (address == null || address.getLabel() == null || address.getLabel().isBlank()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(address.getLabel());
        appendAddressPart(builder, address.getAddressLine1());
        appendAddressPart(builder, address.getAddressLine2());
        appendAddressPart(builder, address.getCity());
        appendAddressPart(builder, address.getPostalCode());
        return builder.toString();
    }

    private void appendAddressPart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(", ");
        }
        builder.append(value);
    }

    private boolean isOutForDeliveryOrder(OrderEntity order) {
        return "OUT_FOR_DELIVERY".equals(normalizeStatus(order.getOrderStatus()));
    }

    private boolean isCompletedStatus(String status) {
        String normalized = normalizeStatus(status);
        return "DELIVERED".equals(normalized) || "COMPLETED".equals(normalized);
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private record CancellationExtras(String reason, String refundPolicyApplied) {
    }
}
