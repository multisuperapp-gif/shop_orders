package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.OrderEntity;
import com.msa.shop_orders.persistence.entity.OrderItemEntity;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import com.msa.shop_orders.persistence.repository.OrderItemRepository;
import com.msa.shop_orders.persistence.repository.OrderRepository;
import com.msa.shop_orders.persistence.repository.ProductRepository;
import com.msa.shop_orders.persistence.repository.ProductVariantRepository;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardMetricData;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardSummaryData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderItemData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShopOrderReadServiceImpl implements ShopOrderReadService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    public ShopOrderReadServiceImpl(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductVariantRepository productVariantRepository,
            ProductRepository productRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
    }

    @Override
    public ShopDashboardSummaryData summary(Long shopId) {
        List<OrderEntity> orders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopId);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return new ShopDashboardSummaryData(
                metric(orders, todayStart),
                metric(orders, monthStart),
                metric(orders, weekStart),
                metric(orders, null)
        );
    }

    @Override
    public List<ShopOrderData> liveOrders(Long shopId) {
        List<OrderEntity> liveOrders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopId).stream()
                .filter(order -> !"DELIVERED".equalsIgnoreCase(order.getOrderStatus()))
                .filter(order -> !"CANCELLED".equalsIgnoreCase(order.getOrderStatus()))
                .toList();
        return buildOrderCards(liveOrders);
    }

    @Override
    public List<ShopOrderData> orders(Long shopId, String dateFilter, String status, LocalDate fromDate, LocalDate toDate) {
        List<OrderEntity> orders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopId);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            orders = orders.stream()
                    .filter(order -> status.equalsIgnoreCase(order.getOrderStatus()))
                    .toList();
        }
        String normalizedFilter = dateFilter == null ? "ALL" : dateFilter.trim().toUpperCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        orders = switch (normalizedFilter) {
            case "TODAY" -> orders.stream().filter(order -> sameDay(order.getCreatedAt(), today)).toList();
            case "LAST_7_DAYS" -> orders.stream().filter(order -> withinDays(order.getCreatedAt(), 7)).toList();
            case "LAST_30_DAYS" -> orders.stream().filter(order -> withinDays(order.getCreatedAt(), 30)).toList();
            case "CUSTOM" -> orders.stream().filter(order -> withinCustomRange(order.getCreatedAt(), fromDate, toDate)).toList();
            default -> orders;
        };
        return buildOrderCards(orders);
    }

    private ShopDashboardMetricData metric(List<OrderEntity> orders, LocalDateTime start) {
        List<OrderEntity> filtered = start == null ? orders : orders.stream()
                .filter(order -> order.getCreatedAt() != null && !order.getCreatedAt().isBefore(start))
                .toList();
        long completed = filtered.stream().filter(order -> "DELIVERED".equalsIgnoreCase(order.getOrderStatus())).count();
        long cancelled = filtered.stream().filter(order -> "CANCELLED".equalsIgnoreCase(order.getOrderStatus())).count();
        BigDecimal orderValue = filtered.stream()
                .map(OrderEntity::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ShopDashboardMetricData(filtered.size(), completed, cancelled, orderValue);
    }

    private List<ShopOrderData> buildOrderCards(List<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(OrderEntity::getId).toList();
        Map<Long, List<OrderItemEntity>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        Collection<Long> variantIds = itemsByOrderId.values().stream()
                .flatMap(List::stream)
                .map(OrderItemEntity::getVariantId)
                .collect(Collectors.toSet());
        Map<Long, ProductVariantEntity> variantsById = productVariantRepository.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        Collection<Long> productIds = variantsById.values().stream()
                .map(ProductVariantEntity::getProductId)
                .collect(Collectors.toSet());
        Map<Long, ProductEntity> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        return orders.stream().map(order -> {
            List<ShopOrderItemData> itemData = itemsByOrderId.getOrDefault(order.getId(), List.of()).stream()
                    .map(item -> toOrderItemData(item, variantsById, productsById))
                    .toList();
            int itemCount = itemData.stream()
                    .map(ShopOrderItemData::quantity)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
            return new ShopOrderData(
                    order.getId(),
                    order.getOrderCode(),
                    order.getOrderStatus(),
                    order.getCreatedAt(),
                    itemCount,
                    defaultAmount(order.getSubtotalAmount()),
                    defaultAmount(order.getDeliveryFeeAmount()),
                    defaultAmount(order.getPlatformFeeAmount()),
                    defaultAmount(order.getTotalAmount()),
                    itemData
            );
        }).toList();
    }

    private ShopOrderItemData toOrderItemData(
            OrderItemEntity item,
            Map<Long, ProductVariantEntity> variantsById,
            Map<Long, ProductEntity> productsById
    ) {
        ProductVariantEntity variant = variantsById.get(item.getVariantId());
        ProductEntity product = variant == null ? null : productsById.get(variant.getProductId());
        String itemName = product == null ? "Item" : product.getName();
        if (variant != null && variant.getVariantName() != null && !variant.getVariantName().isBlank() && !variant.getVariantName().equalsIgnoreCase(itemName)) {
            itemName = itemName + " (" + variant.getVariantName() + ")";
        }
        return new ShopOrderItemData(
                itemName,
                item.getQuantity(),
                formatUnitLabel(variant),
                defaultAmount(item.getUnitPriceSnapshot()),
                defaultAmount(item.getLineTotal())
        );
    }

    private boolean sameDay(LocalDateTime createdAt, LocalDate day) {
        return createdAt != null && createdAt.toLocalDate().isEqual(day);
    }

    private boolean withinDays(LocalDateTime createdAt, int days) {
        return createdAt != null && !createdAt.toLocalDate().isBefore(LocalDate.now().minusDays(days - 1L));
    }

    private boolean withinCustomRange(LocalDateTime createdAt, LocalDate fromDate, LocalDate toDate) {
        if (createdAt == null) {
            return false;
        }
        LocalDate createdDate = createdAt.toLocalDate();
        if (fromDate != null && createdDate.isBefore(fromDate)) {
            return false;
        }
        return toDate == null || !createdDate.isAfter(toDate);
    }

    private String formatUnitLabel(ProductVariantEntity variant) {
        if (variant == null || variant.getUnitValue() == null || variant.getUnitType() == null || variant.getUnitType().isBlank()) {
            return null;
        }
        return variant.getUnitValue().stripTrailingZeros().toPlainString() + " " + variant.getUnitType();
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
