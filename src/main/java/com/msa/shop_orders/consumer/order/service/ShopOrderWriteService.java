package com.msa.shop_orders.consumer.order.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.InventoryEntity;
import com.msa.shop_orders.persistence.entity.OrderEntity;
import com.msa.shop_orders.persistence.entity.OrderItemEntity;
import com.msa.shop_orders.persistence.entity.OrderStatusHistoryEntity;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import com.msa.shop_orders.persistence.entity.ShopDeliveryRuleEntity;
import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import com.msa.shop_orders.persistence.entity.UserAddressEntity;
import com.msa.shop_orders.persistence.repository.InventoryRepository;
import com.msa.shop_orders.persistence.repository.OrderItemRepository;
import com.msa.shop_orders.persistence.repository.OrderRepository;
import com.msa.shop_orders.persistence.repository.OrderStatusHistoryRepository;
import com.msa.shop_orders.persistence.repository.ProductRepository;
import com.msa.shop_orders.persistence.repository.ProductVariantRepository;
import com.msa.shop_orders.persistence.repository.ShopDeliveryRuleRepository;
import com.msa.shop_orders.persistence.repository.ShopLocationRepository;
import com.msa.shop_orders.persistence.repository.UserAddressRepository;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ShopOrderWriteService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final ShopLocationRepository shopLocationRepository;
    private final ShopDeliveryRuleRepository shopDeliveryRuleRepository;
    private final UserAddressRepository userAddressRepository;
    private final ShopShellViewRepository shopShellViewRepository;

    public ShopOrderWriteService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            InventoryRepository inventoryRepository,
            ShopLocationRepository shopLocationRepository,
            ShopDeliveryRuleRepository shopDeliveryRuleRepository,
            UserAddressRepository userAddressRepository,
            ShopShellViewRepository shopShellViewRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
        this.shopLocationRepository = shopLocationRepository;
        this.shopDeliveryRuleRepository = shopDeliveryRuleRepository;
        this.userAddressRepository = userAddressRepository;
        this.shopShellViewRepository = shopShellViewRepository;
    }

    public CreatedOrder createOrder(CreateOrderCommand command) {
        validateItems(command.items());
        UserAddressEntity address = userAddressRepository.findById(command.addressId())
                .filter(row -> command.userId().equals(row.getUserId()))
                .filter(row -> "CONSUMER".equalsIgnoreCase(row.getAddressScope()))
                .orElseThrow(() -> new BusinessException("ADDRESS_NOT_FOUND", "Address not found.", HttpStatus.NOT_FOUND));

        Map<Long, Integer> quantitiesByVariant = mergeQuantities(command.items());
        List<ProductVariantEntity> variants = productVariantRepository.findAllById(quantitiesByVariant.keySet());
        if (variants.size() != quantitiesByVariant.size()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "One or more selected variants are no longer available.", HttpStatus.NOT_FOUND);
        }

        Map<Long, ProductVariantEntity> variantsById = variants.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), LinkedHashMap::putAll);
        Map<Long, ProductEntity> productsById = productRepository.findAllById(variants.stream().map(ProductVariantEntity::getProductId).distinct().toList())
                .stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getId(), item), LinkedHashMap::putAll);
        Map<Long, InventoryEntity> inventoryByVariantId = inventoryRepository.findByVariantIdIn(quantitiesByVariant.keySet())
                .stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.getVariantId(), item), LinkedHashMap::putAll);
        Map<Long, CreateOrderItemCommand> commandsByVariantId = command.items().stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.variantId(), item), LinkedHashMap::putAll);

        Long shopId = resolveSingleShop(variants, productsById);
        ShopLocationEntity primaryLocation = shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shopId)
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Primary approved shop location is not available.", HttpStatus.BAD_REQUEST));
        ShopDeliveryRuleEntity deliveryRule = shopDeliveryRuleRepository.findByShopLocationId(primaryLocation.getId()).orElse(null);
        ShopShellView shop = shopShellViewRepository.findById(shopId).orElse(null);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<CreatedOrderItem> createdItems = new ArrayList<>();
        for (ProductVariantEntity variant : variants) {
            ProductEntity product = productsById.get(variant.getProductId());
            InventoryEntity inventory = inventoryByVariantId.get(variant.getId());
            CreateOrderItemCommand itemCommand = commandsByVariantId.get(variant.getId());
            Integer requestedQuantity = quantitiesByVariant.get(variant.getId());
            validateVariant(product, variant, inventory, requestedQuantity);
            BigDecimal unitPrice = defaultAmount(variant.getSellingPrice());
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(requestedQuantity));
            subtotal = subtotal.add(lineTotal);
            createdItems.add(new CreatedOrderItem(
                    product.getId(),
                    variant.getId(),
                    requestedQuantity,
                    unitPrice,
                    lineTotal,
                    itemCommand.productNameSnapshot() == null || itemCommand.productNameSnapshot().isBlank() ? product.getName() : itemCommand.productNameSnapshot(),
                    itemCommand.variantNameSnapshot() == null || itemCommand.variantNameSnapshot().isBlank() ? variant.getVariantName() : itemCommand.variantNameSnapshot(),
                    itemCommand.imageFileIdSnapshot(),
                    itemCommand.selectedOptionsJson() == null || itemCommand.selectedOptionsJson().isBlank() ? "{\"optionIds\":[],\"optionNames\":[]}" : itemCommand.selectedOptionsJson()
            ));
        }

        String fulfillmentType = normalizeFulfillmentType(command.fulfillmentType());
        BigDecimal deliveryFee = resolveDeliveryFee(fulfillmentType, deliveryRule, subtotal);
        BigDecimal platformFee = command.platformFeeAmount() == null ? BigDecimal.ZERO : command.platformFeeAmount();
        BigDecimal totalAmount = subtotal.add(deliveryFee).add(platformFee);

        OrderEntity order = new OrderEntity();
        order.setOrderCode(generateOrderCode());
        order.setUserId(command.userId());
        order.setShopId(shopId);
        order.setShopLocationId(primaryLocation.getId());
        order.setAddressId(address.getId());
        order.setOrderStatus(command.orderStatus());
        order.setPaymentStatus(command.paymentStatus());
        order.setFulfillmentType(fulfillmentType);
        order.setSubtotalAmount(subtotal);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setDeliveryFeeAmount(deliveryFee);
        order.setPlatformFeeAmount(platformFee);
        order.setPackagingFeeAmount(BigDecimal.ZERO);
        order.setTipAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(totalAmount);
        order.setCurrencyCode(command.currencyCode() == null || command.currencyCode().isBlank() ? "INR" : command.currencyCode());
        OrderEntity savedOrder = orderRepository.save(order);

        List<OrderItemEntity> orderItems = new ArrayList<>();
        for (CreatedOrderItem item : createdItems) {
            InventoryEntity inventory = inventoryByVariantId.get(item.variantId());
            inventory.setReservedQuantity(defaultInteger(inventory.getReservedQuantity()) + item.quantity());

            OrderItemEntity orderItem = new OrderItemEntity();
            orderItem.setOrderId(savedOrder.getId());
            orderItem.setProductId(item.productId());
            orderItem.setVariantId(item.variantId());
            orderItem.setSelectedOptionsJson(item.selectedOptionsJson());
            orderItem.setProductNameSnapshot(item.productNameSnapshot());
            orderItem.setVariantNameSnapshot(item.variantNameSnapshot());
            orderItem.setImageFileIdSnapshot(item.imageFileIdSnapshot());
            orderItem.setShopNameSnapshot(shop == null || shop.getShopName() == null || shop.getShopName().isBlank() ? "Shop" : shop.getShopName());
            orderItem.setQuantity(item.quantity());
            orderItem.setUnitPriceSnapshot(item.unitPrice());
            orderItem.setTaxSnapshot(BigDecimal.ZERO);
            orderItem.setLineTotal(item.lineTotal());
            orderItems.add(orderItem);
        }
        orderItemRepository.saveAll(orderItems);
        inventoryRepository.saveAll(inventoryByVariantId.values());

        OrderStatusHistoryEntity history = new OrderStatusHistoryEntity();
        history.setOrderId(savedOrder.getId());
        history.setNewStatus(command.orderStatus());
        history.setChangedByUserId(command.userId());
        history.setReason(command.historyReason());
        history.setChangedAt(command.createdAt());
        orderStatusHistoryRepository.save(history);

        return new CreatedOrder(
                savedOrder.getId(),
                savedOrder.getOrderCode(),
                savedOrder.getShopId(),
                savedOrder.getUserId(),
                savedOrder.getOrderStatus(),
                savedOrder.getPaymentStatus(),
                savedOrder.getFulfillmentType(),
                savedOrder.getSubtotalAmount(),
                savedOrder.getDeliveryFeeAmount(),
                savedOrder.getTotalAmount(),
                savedOrder.getPlatformFeeAmount(),
                savedOrder.getCurrencyCode(),
                savedOrder.getCreatedAt() == null ? command.createdAt() : savedOrder.getCreatedAt(),
                shop == null || shop.getShopName() == null || shop.getShopName().isBlank() ? "Shop" : shop.getShopName(),
                command.addressLabel(),
                command.addressLine(),
                command.paymentCode(),
                createdItems
        );
    }

    private void validateItems(List<CreateOrderItemCommand> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("ORDER_INVALID", "At least one shop order item is required.", HttpStatus.BAD_REQUEST);
        }
        for (CreateOrderItemCommand item : items) {
            if (item == null || item.variantId() == null || item.quantity() == null || item.quantity() < 1) {
                throw new BusinessException("ORDER_INVALID", "Order item is incomplete.", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private Map<Long, Integer> mergeQuantities(List<CreateOrderItemCommand> items) {
        Map<Long, Integer> quantitiesByVariant = new LinkedHashMap<>();
        for (CreateOrderItemCommand item : items) {
            quantitiesByVariant.merge(item.variantId(), item.quantity(), Integer::sum);
        }
        return quantitiesByVariant;
    }

    private Long resolveSingleShop(List<ProductVariantEntity> variants, Map<Long, ProductEntity> productsById) {
        Long shopId = null;
        for (ProductVariantEntity variant : variants) {
            ProductEntity product = productsById.get(variant.getProductId());
            if (product == null) {
                throw new BusinessException("PRODUCT_NOT_FOUND", "Selected product is no longer available.", HttpStatus.NOT_FOUND);
            }
            if (shopId == null) {
                shopId = product.getShopId();
            } else if (!Objects.equals(shopId, product.getShopId())) {
                throw new BusinessException("ORDER_INVALID", "Items from multiple shops cannot be ordered together.", HttpStatus.BAD_REQUEST);
            }
        }
        if (shopId == null) {
            throw new BusinessException("ORDER_INVALID", "At least one shop order item is required.", HttpStatus.BAD_REQUEST);
        }
        return shopId;
    }

    private void validateVariant(ProductEntity product, ProductVariantEntity variant, InventoryEntity inventory, Integer requestedQuantity) {
        if (product == null || variant == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Selected product is no longer available.", HttpStatus.NOT_FOUND);
        }
        if (!product.isActive() || !variant.isActive()) {
            throw new BusinessException("PRODUCT_INACTIVE", "Selected product is inactive.", HttpStatus.BAD_REQUEST);
        }
        if (inventory == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product inventory not found.", HttpStatus.NOT_FOUND);
        }
        if (!"IN_STOCK".equalsIgnoreCase(inventory.getInventoryStatus())
                && !"LOW_STOCK".equalsIgnoreCase(inventory.getInventoryStatus())) {
            throw new BusinessException("OUT_OF_STOCK", "Selected item is out of stock.", HttpStatus.BAD_REQUEST);
        }
        int availableToReserve = defaultInteger(inventory.getQuantityAvailable()) - defaultInteger(inventory.getReservedQuantity());
        if (availableToReserve < requestedQuantity) {
            throw new BusinessException("OUT_OF_STOCK", "Requested quantity is not available for variant " + variant.getId() + ".", HttpStatus.BAD_REQUEST);
        }
    }

    private String normalizeFulfillmentType(String value) {
        if (value == null || value.isBlank()) {
            return "DELIVERY";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"DELIVERY".equals(normalized) && !"PICKUP".equals(normalized)) {
            throw new BusinessException("ORDER_INVALID", "Unsupported fulfillment type.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private BigDecimal resolveDeliveryFee(String fulfillmentType, ShopDeliveryRuleEntity deliveryRule, BigDecimal subtotal) {
        if ("PICKUP".equalsIgnoreCase(fulfillmentType)) {
            return BigDecimal.ZERO;
        }
        String deliveryType = deliveryRule == null || deliveryRule.getDeliveryType() == null ? "DELIVERY" : deliveryRule.getDeliveryType();
        if ("PICKUP_ONLY".equalsIgnoreCase(deliveryType)) {
            throw new BusinessException("ORDER_INVALID", "This shop currently supports pickup only.", HttpStatus.BAD_REQUEST);
        }
        BigDecimal minOrderAmount = deliveryRule == null ? BigDecimal.ZERO : defaultAmount(deliveryRule.getMinOrderAmount());
        if (subtotal.compareTo(minOrderAmount) < 0) {
            throw new BusinessException("ORDER_INVALID", "Minimum order amount for delivery is " + minOrderAmount + ".", HttpStatus.BAD_REQUEST);
        }
        BigDecimal freeDeliveryAbove = deliveryRule == null ? null : deliveryRule.getFreeDeliveryAbove();
        if (freeDeliveryAbove != null && subtotal.compareTo(freeDeliveryAbove) >= 0) {
            return BigDecimal.ZERO;
        }
        return deliveryRule == null ? BigDecimal.ZERO : defaultAmount(deliveryRule.getDeliveryFee());
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private String generateOrderCode() {
        return "ORD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    public record CreateOrderCommand(
            Long userId,
            Long addressId,
            String fulfillmentType,
            BigDecimal platformFeeAmount,
            String currencyCode,
            String orderStatus,
            String paymentStatus,
            String historyReason,
            LocalDateTime createdAt,
            String addressLabel,
            String addressLine,
            String paymentCode,
            List<CreateOrderItemCommand> items
    ) {
    }

    public record CreateOrderItemCommand(
            Long variantId,
            Integer quantity,
            String productNameSnapshot,
            String variantNameSnapshot,
            Long imageFileIdSnapshot,
            String selectedOptionsJson
    ) {
    }

    public record CreatedOrderItem(
            Long productId,
            Long variantId,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String productNameSnapshot,
            String variantNameSnapshot,
            Long imageFileIdSnapshot,
            String selectedOptionsJson
    ) {
    }

    public record CreatedOrder(
            Long orderId,
            String orderCode,
            Long shopId,
            Long userId,
            String orderStatus,
            String paymentStatus,
            String fulfillmentType,
            BigDecimal subtotalAmount,
            BigDecimal deliveryFeeAmount,
            BigDecimal totalAmount,
            BigDecimal platformFeeAmount,
            String currencyCode,
            LocalDateTime createdAt,
            String shopName,
            String addressLabel,
            String addressLine,
            String paymentCode,
            List<CreatedOrderItem> items
    ) {
    }
}
