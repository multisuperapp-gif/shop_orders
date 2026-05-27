package com.msa.shop_orders.consumer.order.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.RestaurantVariantPromotionSupport;
import com.msa.shop_orders.common.shoptype.RestaurantItemVisibilityPolicy;
import com.msa.shop_orders.common.mongo.MongoSequenceService;
import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import com.msa.shop_orders.persistence.entity.UserAddressEntity;
import com.msa.shop_orders.persistence.repository.ShopLocationRepository;
import com.msa.shop_orders.persistence.repository.UserAddressRepository;
import com.msa.shop_orders.provider.shop.dto.ShopProductDeliveryRuleData;
import com.msa.shop_orders.provider.shop.service.ShopDeliveryRuleViewService;
import com.msa.shop_orders.provider.shop.service.ShopShellViewService;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopProductView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ShopLocationRepository shopLocationRepository;
    private final ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    private final UserAddressRepository userAddressRepository;
    private final ShopShellViewRepository shopShellViewRepository;
    private final ShopShellViewService shopShellViewService;
    private final ShopProductViewRepository shopProductViewRepository;
    private final ShopOrderViewRepository shopOrderViewRepository;
    private final MongoSequenceService mongoSequenceService;

    public ShopOrderWriteService(
            ShopLocationRepository shopLocationRepository,
            ShopDeliveryRuleViewService shopDeliveryRuleViewService,
            UserAddressRepository userAddressRepository,
            ShopShellViewRepository shopShellViewRepository,
            ShopShellViewService shopShellViewService,
            ShopProductViewRepository shopProductViewRepository,
            ShopOrderViewRepository shopOrderViewRepository,
            MongoSequenceService mongoSequenceService
    ) {
        this.shopLocationRepository = shopLocationRepository;
        this.shopDeliveryRuleViewService = shopDeliveryRuleViewService;
        this.userAddressRepository = userAddressRepository;
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopShellViewService = shopShellViewService;
        this.shopProductViewRepository = shopProductViewRepository;
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.mongoSequenceService = mongoSequenceService;
    }

    public CreatedOrder createOrder(CreateOrderCommand command) {
        return createOrderDocument(command);
    }

    private CreatedOrder createOrderDocument(CreateOrderCommand command) {
        validateItems(command.items());
        UserAddressEntity address = userAddressRepository.findById(command.addressId())
                .filter(row -> command.userId().equals(row.getUserId()))
                .filter(row -> "CONSUMER".equalsIgnoreCase(row.getAddressScope()))
                .orElseThrow(() -> new BusinessException("ADDRESS_NOT_FOUND", "Address not found.", HttpStatus.NOT_FOUND));

        Map<Long, Integer> quantitiesByVariant = mergeQuantities(command.items());
        List<ShopProductView> products = shopProductViewRepository.findByVariantsVariantIdIn(quantitiesByVariant.keySet());
        Map<Long, VariantSelection> selectionsByVariantId = new LinkedHashMap<>();
        for (ShopProductView product : products) {
            for (ShopProductView.Variant variant : safeVariants(product)) {
                if (quantitiesByVariant.containsKey(variant.getVariantId())) {
                    selectionsByVariantId.put(variant.getVariantId(), new VariantSelection(product, variant));
                }
            }
        }
        if (selectionsByVariantId.size() != quantitiesByVariant.size()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "One or more selected variants are no longer available.", HttpStatus.NOT_FOUND);
        }
        Long shopId = resolveSingleShop(selectionsByVariantId.values());
        ShopLocationEntity primaryLocation = shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shopId)
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Primary approved shop location is not available.", HttpStatus.BAD_REQUEST));
        ShopProductDeliveryRuleData deliveryRule = shopDeliveryRuleViewService.findPrimaryDeliveryRule(shopId)
                .orElse(new ShopProductDeliveryRuleData(primaryLocation.getId(), "DELIVERY", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("999999999.99"), 30, 60));
        ShopShellView shop = shopShellViewService.findByShopId(shopId)
                .or(() -> shopShellViewRepository.findById(shopId))
                .orElse(null);
        validateItemsForRestaurantType(shop, selectionsByVariantId);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<CreatedOrderItem> createdItems = new ArrayList<>();
        Map<Long, ShopProductView> mutatedProductsById = new LinkedHashMap<>();
        Map<Long, CreateOrderItemCommand> commandsByVariantId = command.items().stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.variantId(), item), LinkedHashMap::putAll);

        for (Map.Entry<Long, Integer> entry : quantitiesByVariant.entrySet()) {
            Long variantId = entry.getKey();
            Integer requestedQuantity = entry.getValue();
            VariantSelection selection = selectionsByVariantId.get(variantId);
            ShopProductView product = selection.product();
            ShopProductView.Variant variant = selection.variant();
            validateVariant(product, variant, requestedQuantity);
            variant.setReservedQuantity(defaultInteger(variant.getReservedQuantity()) + requestedQuantity);
            variant.setInventoryStatus(resolveInventoryStatus(
                    variant.getQuantityAvailable(),
                    variant.getReservedQuantity(),
                    variant.getReorderLevel(),
                    product.isActive() && variant.isActive()
            ));
            BigDecimal unitPrice = RestaurantVariantPromotionSupport.resolveEffectiveSellingPrice(product, variant);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(requestedQuantity));
            subtotal = subtotal.add(lineTotal);
            CreateOrderItemCommand itemCommand = commandsByVariantId.get(variantId);
            createdItems.add(new CreatedOrderItem(
                    product.getProductId(),
                    variant.getVariantId(),
                    requestedQuantity,
                    unitPrice,
                    lineTotal,
                    itemCommand.productNameSnapshot() == null || itemCommand.productNameSnapshot().isBlank() ? product.getItemName() : itemCommand.productNameSnapshot(),
                    itemCommand.variantNameSnapshot() == null || itemCommand.variantNameSnapshot().isBlank() ? variant.getVariantName() : itemCommand.variantNameSnapshot(),
                    itemCommand.imageFileIdSnapshot() == null ? resolvePrimaryImageFileId(product) : itemCommand.imageFileIdSnapshot(),
                    itemCommand.selectedOptionsJson() == null || itemCommand.selectedOptionsJson().isBlank() ? "{\"optionIds\":[],\"optionNames\":[]}" : itemCommand.selectedOptionsJson()
            ));
            updateProductSummaryFromVariants(product);
            mutatedProductsById.put(product.getProductId(), product);
        }

        String fulfillmentType = normalizeFulfillmentType(command.fulfillmentType());
        BigDecimal deliveryFee = resolveDeliveryFee(fulfillmentType, deliveryRule, subtotal);
        BigDecimal platformFee = command.platformFeeAmount() == null ? BigDecimal.ZERO : command.platformFeeAmount();
        BigDecimal couponEligibleSubtotal = resolveCouponEligibleSubtotal(createdItems, selectionsByVariantId);
        BigDecimal discountAmount = resolveRestaurantCouponDiscount(shop, couponEligibleSubtotal);
        BigDecimal totalAmount = subtotal.add(deliveryFee).add(platformFee).subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        Long orderId = mongoSequenceService.nextValue("shop-order-id");
        LocalDateTime createdAt = command.createdAt() == null ? LocalDateTime.now() : command.createdAt();

        ShopOrderView document = new ShopOrderView();
        document.setOrderId(orderId);
        document.setShopId(shopId);
        document.setUserId(command.userId());
        document.setOrderCode(generateOrderCode());
        document.setShopName(shop == null || shop.getShopName() == null || shop.getShopName().isBlank() ? "Shop" : shop.getShopName());
        document.setOrderStatus(command.orderStatus());
        document.setPaymentStatus(command.paymentStatus());
        document.setPaymentCode(command.paymentCode());
        document.setFulfillmentType(fulfillmentType);
        document.setAddressLabel(command.addressLabel());
        document.setAddressLine(command.addressLine());
        document.setRefundPresent(false);
        document.setLatestRefundStatus(null);
        document.setRefund(null);
        document.setCreatedAt(createdAt);
        document.setUpdatedAt(createdAt);
        document.setItemCount(createdItems.stream().map(CreatedOrderItem::quantity).filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        document.setSubtotalAmount(subtotal);
        document.setTaxAmount(BigDecimal.ZERO);
        document.setItemsTotal(subtotal);
        document.setDeliveryCharges(deliveryFee);
        document.setPlatformFee(platformFee);
        document.setDiscountAmount(discountAmount);
        document.setTotalOrderValue(totalAmount);
        document.setCurrencyCode(command.currencyCode() == null || command.currencyCode().isBlank() ? "INR" : command.currencyCode());
        document.setCancellable(true);
        document.setItems(createdItems.stream().map(this::toOrderItemDocument).toList());
        document.setTimeline(List.of(initialTimeline(command.orderStatus(), createdAt, command.historyReason())));
        shopOrderViewRepository.save(document);
        if (!mutatedProductsById.isEmpty()) {
            shopProductViewRepository.saveAll(mutatedProductsById.values());
        }

        return new CreatedOrder(
                document.getOrderId(),
                document.getOrderCode(),
                document.getShopId(),
                document.getUserId(),
                document.getOrderStatus(),
                document.getPaymentStatus(),
                document.getFulfillmentType(),
                document.getSubtotalAmount(),
                document.getDeliveryCharges(),
                document.getTotalOrderValue(),
                document.getPlatformFee(),
                document.getCurrencyCode(),
                document.getCreatedAt(),
                document.getShopName(),
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

    private void validateItemsForRestaurantType(
            ShopShellView shop,
            Map<Long, VariantSelection> selectionsByVariantId
    ) {
        if (shop == null || selectionsByVariantId == null || selectionsByVariantId.isEmpty()) {
            return;
        }
        boolean hasBlockedItem = selectionsByVariantId.values().stream()
                .map(VariantSelection::product)
                .anyMatch(product -> product == null
                        || !product.isActive()
                        || !RestaurantItemVisibilityPolicy.isCompatible(shop.getRestaurantServiceType(), product.getAttributes()));
        if (hasBlockedItem) {
            throw new BusinessException(
                    "PRODUCT_NOT_FOUND",
                    "One or more selected items are no longer available for this restaurant type.",
                    HttpStatus.NOT_FOUND
            );
        }
    }

    private Map<Long, Integer> mergeQuantities(List<CreateOrderItemCommand> items) {
        Map<Long, Integer> quantitiesByVariant = new LinkedHashMap<>();
        for (CreateOrderItemCommand item : items) {
            quantitiesByVariant.merge(item.variantId(), item.quantity(), Integer::sum);
        }
        return quantitiesByVariant;
    }

    private Long resolveSingleShop(Iterable<VariantSelection> selections) {
        Long shopId = null;
        for (VariantSelection selection : selections) {
            if (shopId == null) {
                shopId = selection.product().getShopId();
            } else if (!Objects.equals(shopId, selection.product().getShopId())) {
                throw new BusinessException("ORDER_INVALID", "Items from multiple shops cannot be ordered together.", HttpStatus.BAD_REQUEST);
            }
        }
        if (shopId == null) {
            throw new BusinessException("ORDER_INVALID", "At least one shop order item is required.", HttpStatus.BAD_REQUEST);
        }
        return shopId;
    }

    private void validateVariant(ShopProductView product, ShopProductView.Variant variant, Integer requestedQuantity) {
        if (product == null || variant == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Selected product is no longer available.", HttpStatus.NOT_FOUND);
        }
        if (!product.isActive() || !variant.isActive()) {
            throw new BusinessException("PRODUCT_INACTIVE", "Selected product is inactive.", HttpStatus.BAD_REQUEST);
        }
        int availableToReserve = defaultInteger(variant.getQuantityAvailable()) - defaultInteger(variant.getReservedQuantity());
        if (availableToReserve < requestedQuantity) {
            throw new BusinessException("OUT_OF_STOCK", "Requested quantity is not available for variant " + variant.getVariantId() + ".", HttpStatus.BAD_REQUEST);
        }
        String inventoryStatus = variant.getInventoryStatus();
        if (!"IN_STOCK".equalsIgnoreCase(inventoryStatus)
                && !"LOW_STOCK".equalsIgnoreCase(inventoryStatus)) {
            throw new BusinessException("OUT_OF_STOCK", "Selected item is out of stock.", HttpStatus.BAD_REQUEST);
        }
    }

    private List<ShopProductView.Variant> safeVariants(ShopProductView product) {
        return product.getVariants() == null ? List.of() : product.getVariants();
    }

    private Long resolvePrimaryImageFileId(ShopProductView product) {
        if (product.getImages() == null || product.getImages().isEmpty()) {
            return product.getImageFileId();
        }
        return product.getImages().stream()
                .filter(ShopProductView.Image::isPrimaryImage)
                .map(ShopProductView.Image::getFileId)
                .findFirst()
                .orElse(product.getImageFileId());
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
        product.setInventoryStatus(primary.getInventoryStatus());
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

    private ShopOrderView.Item toOrderItemDocument(CreatedOrderItem item) {
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
        return document;
    }

    private ShopOrderView.TimelineEvent initialTimeline(String orderStatus, LocalDateTime changedAt, String reason) {
        ShopOrderView.TimelineEvent event = new ShopOrderView.TimelineEvent();
        event.setOldStatus(null);
        event.setNewStatus(orderStatus);
        event.setReason(reason);
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

    private BigDecimal resolveDeliveryFee(String fulfillmentType, ShopProductDeliveryRuleData deliveryRule, BigDecimal subtotal) {
        if ("PICKUP".equalsIgnoreCase(fulfillmentType)) {
            return BigDecimal.ZERO;
        }
        String deliveryType = deliveryRule == null || deliveryRule.deliveryType() == null ? "DELIVERY" : deliveryRule.deliveryType();
        if ("PICKUP_ONLY".equalsIgnoreCase(deliveryType)) {
            throw new BusinessException("ORDER_INVALID", "This shop currently supports pickup only.", HttpStatus.BAD_REQUEST);
        }
        BigDecimal minOrderAmount = deliveryRule == null ? BigDecimal.ZERO : defaultAmount(deliveryRule.minOrderAmount());
        if (subtotal.compareTo(minOrderAmount) < 0) {
            throw new BusinessException("ORDER_INVALID", "Minimum order amount for delivery is " + minOrderAmount + ".", HttpStatus.BAD_REQUEST);
        }
        BigDecimal freeDeliveryAbove = deliveryRule == null ? null : deliveryRule.freeDeliveryAbove();
        if (freeDeliveryAbove != null && subtotal.compareTo(freeDeliveryAbove) >= 0) {
            return BigDecimal.ZERO;
        }
        return deliveryRule == null ? BigDecimal.ZERO : defaultAmount(deliveryRule.deliveryFee());
    }

    private BigDecimal resolveCouponEligibleSubtotal(
            List<CreatedOrderItem> createdItems,
            Map<Long, VariantSelection> selectionsByVariantId
    ) {
        if (createdItems == null || createdItems.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal eligibleSubtotal = BigDecimal.ZERO;
        for (CreatedOrderItem item : createdItems) {
            if (item == null) {
                continue;
            }
            VariantSelection selection = selectionsByVariantId.get(item.variantId());
            if (selection != null && RestaurantVariantPromotionSupport.hasActivePromotion(selection.product(), selection.variant())) {
                continue;
            }
            eligibleSubtotal = eligibleSubtotal.add(defaultAmount(item.lineTotal()));
        }
        return eligibleSubtotal.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal resolveRestaurantCouponDiscount(ShopShellView shop, BigDecimal subtotal) {
        if (shop == null || shop.getRestaurantCoupon() == null || subtotal == null) {
            return BigDecimal.ZERO;
        }
        ShopShellView.RestaurantCoupon coupon = shop.getRestaurantCoupon();
        if (!Boolean.TRUE.equals(coupon.getActive())) {
            return BigDecimal.ZERO;
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartsAt() == null || coupon.getEndsAt() == null
                || now.isBefore(coupon.getStartsAt()) || now.isAfter(coupon.getEndsAt())) {
            return BigDecimal.ZERO;
        }
        BigDecimal minOrderAmount = defaultAmount(coupon.getMinOrderAmount());
        if (subtotal.compareTo(minOrderAmount) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discountValue = defaultAmount(coupon.getDiscountValue());
        if (discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal discountAmount;
        if ("FLAT".equalsIgnoreCase(coupon.getDiscountType())) {
            discountAmount = discountValue;
        } else {
            discountAmount = subtotal.multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        BigDecimal maxDiscountAmount = coupon.getMaxDiscountAmount();
        if (maxDiscountAmount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) > 0
                && discountAmount.compareTo(maxDiscountAmount) > 0) {
            discountAmount = maxDiscountAmount;
        }
        if (discountAmount.compareTo(subtotal) > 0) {
            return subtotal.setScale(2, RoundingMode.HALF_UP);
        }
        return discountAmount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
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

    private record VariantSelection(ShopProductView product, ShopProductView.Variant variant) {
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
