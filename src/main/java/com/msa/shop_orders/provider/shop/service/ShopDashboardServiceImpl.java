package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.*;
import com.msa.shop_orders.persistence.repository.*;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardMetricData;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardSummaryData;
import com.msa.shop_orders.provider.shop.dto.ShopInventoryAlertData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderItemData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShopDashboardServiceImpl implements ShopDashboardService {
    private final ShopContextService shopContextService;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ShopInventoryCategoryRepository shopInventoryCategoryRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public ShopDashboardServiceImpl(
            ShopContextService shopContextService,
            ProductVariantRepository productVariantRepository,
            ProductRepository productRepository,
            ShopInventoryCategoryRepository shopInventoryCategoryRepository,
            ShopCategoryRepository shopCategoryRepository,
            InventoryRepository inventoryRepository,
            ProductImageRepository productImageRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.shopContextService = shopContextService;
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
        this.shopInventoryCategoryRepository = shopInventoryCategoryRepository;
        this.shopCategoryRepository = shopCategoryRepository;
        this.inventoryRepository = inventoryRepository;
        this.productImageRepository = productImageRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    public ShopDashboardSummaryData summary() {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        List<OrderEntity> orders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getId());
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
    public List<ShopOrderData> liveOrders() {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        List<OrderEntity> liveOrders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getId()).stream()
                .filter(order -> !"DELIVERED".equalsIgnoreCase(order.getOrderStatus()))
                .filter(order -> !"CANCELLED".equalsIgnoreCase(order.getOrderStatus()))
                .toList();
        return buildOrderCards(liveOrders);
    }

    @Override
    public List<ShopInventoryAlertData> inventoryAlerts() {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        List<ProductEntity> products = productRepository.findByShopIdOrderByUpdatedAtDesc(shopEntity.getId());
        if (products.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductVariantEntity> variantByProductId = productVariantRepository.findByProductIdIn(products.stream().map(ProductEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProductVariantEntity::getProductId, Function.identity(), (left, right) -> left));
        Map<Long, InventoryEntity> inventoryByVariantId = inventoryRepository.findByVariantIdIn(variantByProductId.values().stream().map(ProductVariantEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(InventoryEntity::getVariantId, Function.identity()));
        Map<Long, ShopCategoryEntity> productCategoryById = shopCategoryRepository.findAllById(products.stream().map(ProductEntity::getShopCategoryId).collect(Collectors.toSet()))
                .stream()
                .filter(ShopCategoryEntity::isActive)
                .collect(Collectors.toMap(ShopCategoryEntity::getId, Function.identity()));
        Map<String, Long> shopCategoryIdByName = resolveShopCategoryIdByName(shopEntity.getId());
        Map<Long, ProductImageEntity> imageByProductId = productImageRepository.findByProductIdIn(products.stream().map(ProductEntity::getId).toList())
                .stream()
                .sorted(Comparator.comparing(ProductImageEntity::isPrimaryImage).reversed().thenComparing(ProductImageEntity::getSortOrder))
                .collect(Collectors.toMap(ProductImageEntity::getProductId, Function.identity(), (left, right) -> left));

        return products.stream()
                .map(product -> {
                    ProductVariantEntity variant = variantByProductId.get(product.getId());
                    InventoryEntity inventory = variant == null ? null : inventoryByVariantId.get(variant.getId());
                    ShopCategoryEntity category = productCategoryById.get(product.getShopCategoryId());
                    if (variant == null || inventory == null || category == null) {
                        return null;
                    }
                    boolean alert = "LOW_STOCK".equalsIgnoreCase(inventory.getInventoryStatus())
                            || "OUT_OF_STOCK".equalsIgnoreCase(inventory.getInventoryStatus())
                            || (inventory.getReorderLevel() != null && inventory.getQuantityAvailable() <= inventory.getReorderLevel());
                    if (!alert) {
                        return null;
                    }
                    return new ShopInventoryAlertData(
                            product.getId(),
                            shopCategoryIdByName.get(normalize(category.getName())),
                            category.getName(),
                            product.getName(),
                            variant.getVariantName(),
                            inventory.getQuantityAvailable(),
                            inventory.getReorderLevel(),
                            inventory.getInventoryStatus(),
                            imageByProductId.get(product.getId()) == null ? null : imageByProductId.get(product.getId()).getFileId()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
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

    private Map<String, Long> resolveShopCategoryIdByName(Long shopId) {
        List<ShopInventoryCategoryEntity> mappings = shopInventoryCategoryRepository.findByShopIdOrderByIdAsc(shopId).stream()
                .filter(ShopInventoryCategoryEntity::isEnabled)
                .toList();
        if (mappings.isEmpty()) {
            return Map.of();
        }
        return shopCategoryRepository.findAllById(mappings.stream().map(ShopInventoryCategoryEntity::getShopCategoryId).toList()).stream()
                .collect(Collectors.toMap(category -> normalize(category.getName()), ShopCategoryEntity::getId, (left, right) -> left));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
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
