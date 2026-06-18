package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardMetricData;
import com.msa.shop_orders.provider.shop.dto.ShopDashboardSummaryData;
import com.msa.shop_orders.provider.shop.dto.ShopInventoryAlertData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderItemData;
import com.msa.shop_orders.provider.shop.dto.ShopProductCouponData;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.provider.shop.dto.ShopProductDeliveryRuleData;
import com.msa.shop_orders.provider.shop.dto.ShopProductImageData;
import com.msa.shop_orders.provider.shop.dto.ShopProductPromotionData;
import com.msa.shop_orders.provider.shop.dto.ShopProductVariantData;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopProductView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopProductViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ShopRuntimeViewService {
    private final ShopCategoryViewService shopCategoryViewService;
    private final ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    private final ShopProductViewRepository shopProductViewRepository;
    private final ShopOrderViewRepository shopOrderViewRepository;
    private final ShopShellViewRepository shopShellViewRepository;
    private final com.msa.shop_orders.common.settings.ShopFeeSettingsService shopFeeSettingsService;

    public ShopRuntimeViewService(
            ShopCategoryViewService shopCategoryViewService,
            ShopDeliveryRuleViewService shopDeliveryRuleViewService,
            ShopProductViewRepository shopProductViewRepository,
            ShopOrderViewRepository shopOrderViewRepository,
            ShopShellViewRepository shopShellViewRepository,
            com.msa.shop_orders.common.settings.ShopFeeSettingsService shopFeeSettingsService
    ) {
        this.shopCategoryViewService = shopCategoryViewService;
        this.shopDeliveryRuleViewService = shopDeliveryRuleViewService;
        this.shopProductViewRepository = shopProductViewRepository;
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.shopShellViewRepository = shopShellViewRepository;
        this.shopFeeSettingsService = shopFeeSettingsService;
    }

    public void syncProductsForShop(ShopShellView shopEntity) {
        // Shop runtime source of truth is Mongo; legacy SQL rebuild is intentionally disabled.
    }

    public void syncProductById(ShopShellView shopEntity, Long productId) {
        // Shop runtime source of truth is Mongo; legacy SQL rebuild is intentionally disabled.
    }

    public List<ShopProductData> loadProducts(ShopShellView shopEntity, Long categoryId) {
        List<ShopProductView> documents = categoryId == null
                ? shopProductViewRepository.findByShopIdOrderByUpdatedAtDesc(shopEntity.getShopId())
                : shopProductViewRepository.findByShopIdAndCategoryIdOrderByUpdatedAtDesc(shopEntity.getShopId(), categoryId);
        return documents.stream()
                .map(this::toShopProductData)
                .toList();
    }

    public ShopProductData loadProduct(ShopShellView shopEntity, Long productId) {
        return shopProductViewRepository.findById(productId)
                .filter(document -> Objects.equals(document.getShopId(), shopEntity.getShopId()))
                .map(this::toShopProductData)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
    }

    public void syncOrdersForShop(ShopShellView shopEntity) {
        // Shop runtime source of truth is Mongo; legacy SQL rebuild is intentionally disabled.
    }

    public void syncOrdersForUser(Long userId) {
        // Shop runtime source of truth is Mongo; legacy SQL rebuild is intentionally disabled.
    }

    public void syncOrderById(Long orderId) {
        // Shop runtime source of truth is Mongo; legacy SQL rebuild is intentionally disabled.
    }

    public ShopOrderView buildOrderViewById(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return shopOrderViewRepository.findById(orderId).orElse(null);
    }

    public List<ShopOrderView> loadConsumerOrders(Long userId) {
        return shopOrderViewRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public ShopOrderView loadConsumerOrder(Long userId, Long orderId) {
        return shopOrderViewRepository.findById(orderId)
                .filter(candidate -> Objects.equals(candidate.getUserId(), userId))
                .orElse(null);
    }

    public List<ShopOrderData> loadOrders(ShopShellView shopEntity, String dateFilter, String status, LocalDate fromDate, LocalDate toDate) {
        return filterOrderData(
                shopOrderViewRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getShopId()).stream()
                        .map(this::toShopOrderData)
                        // REJECTED orders (shop rejected, or auto-rejected on no-accept
                        // timeout) are hidden from the shop — admin-only for audit.
                        .filter(order -> !"REJECTED".equalsIgnoreCase(order.orderStatus()))
                        .toList(),
                dateFilter,
                status,
                fromDate,
                toDate
        );
    }

    public ShopOrderData loadOrder(ShopShellView shopEntity, Long orderId) {
        return shopOrderViewRepository.findById(orderId)
                .filter(document -> Objects.equals(document.getShopId(), shopEntity.getShopId()))
                .map(this::toShopOrderData)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found for this shop.", HttpStatus.NOT_FOUND));
    }

    public ShopDashboardSummaryData loadSummary(ShopShellView shopEntity) {
        // Includes REJECTED orders — metric() separates them out as "missed"
        // (declined + not-accepted) so they don't inflate the real-order counts
        // but are still reported as count + value the shop missed.
        List<ShopOrderData> orders = shopOrderViewRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getShopId()).stream()
                .map(this::toShopOrderData)
                .toList();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        // Aggregate the rating straight from the shop's rated orders so it always
        // reflects reality — the stored shell value gets reset to 0 whenever the shop
        // is re-synced from SQL (which never tracks order ratings).
        List<Integer> ratings = orders.stream()
                .map(ShopOrderData::rating)
                .filter(value -> value != null && value > 0)
                .toList();
        BigDecimal avgRating = ratings.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(
                        ratings.stream().mapToInt(Integer::intValue).average().orElse(0))
                    .setScale(2, RoundingMode.HALF_UP);
        return new ShopDashboardSummaryData(
                metric(orders, todayStart),
                metric(orders, monthStart),
                metric(orders, weekStart),
                metric(orders, null),
                avgRating,
                ratings.size()
        );
    }

    public List<ShopOrderData> loadLiveOrders(ShopShellView shopEntity) {
        return shopOrderViewRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getShopId()).stream()
                .map(this::toShopOrderData)
                .filter(order -> !"DELIVERED".equalsIgnoreCase(order.orderStatus()))
                .filter(order -> !"CANCELLED".equalsIgnoreCase(order.orderStatus()))
                .filter(order -> !"REJECTED".equalsIgnoreCase(order.orderStatus()))
                .toList();
    }

    public List<ShopInventoryAlertData> loadInventoryAlerts(ShopShellView shopEntity) {
        List<ShopProductData> products = shopProductViewRepository.findByShopIdOrderByUpdatedAtDesc(shopEntity.getShopId()).stream()
                .map(this::toShopProductData)
                .toList();
        return products.stream()
                .filter(product -> {
                    if ("LOW_STOCK".equalsIgnoreCase(product.inventoryStatus()) || "OUT_OF_STOCK".equalsIgnoreCase(product.inventoryStatus())) {
                        return true;
                    }
                    return product.reorderLevel() != null
                            && product.quantityAvailable() != null
                            && product.quantityAvailable() <= product.reorderLevel();
                })
                .map(product -> new ShopInventoryAlertData(
                        product.productId(),
                        product.categoryId(),
                        product.categoryName(),
                        product.itemName(),
                        product.variantName(),
                        product.quantityAvailable(),
                        product.reorderLevel(),
                        product.inventoryStatus(),
                        product.imageFileId()
                ))
                .toList();
    }

    private List<ShopOrderData> filterOrderData(
            List<ShopOrderData> orders,
            String dateFilter,
            String status,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            orders = orders.stream()
                    .filter(order -> status.equalsIgnoreCase(order.orderStatus()))
                    .toList();
        }
        String normalizedFilter = dateFilter == null ? "ALL" : dateFilter.trim().toUpperCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        return switch (normalizedFilter) {
            case "TODAY" -> orders.stream().filter(order -> sameDay(order.createdAt(), today)).toList();
            case "LAST_7_DAYS" -> orders.stream().filter(order -> withinDays(order.createdAt(), 7)).toList();
            case "LAST_30_DAYS" -> orders.stream().filter(order -> withinDays(order.createdAt(), 30)).toList();
            case "CUSTOM" -> orders.stream().filter(order -> withinCustomRange(order.createdAt(), fromDate, toDate)).toList();
            default -> orders;
        };
    }

    private ShopProductData toShopProductData(ShopProductView document) {
        return new ShopProductData(
                document.getProductId(),
                document.getCategoryId(),
                document.getCategoryName(),
                document.getItemName(),
                document.getShortDescription(),
                document.getDescription(),
                document.getBrandName(),
                document.getVariantName(),
                document.getUnitValue(),
                document.getUnitType(),
                document.getWeightInGrams(),
                document.getMrp(),
                document.getSellingPrice(),
                defaultInteger(document.getQuantityAvailable()),
                defaultInteger(document.getReservedQuantity()),
                document.getReorderLevel(),
                defaultText(document.getInventoryStatus(), "OUT_OF_STOCK"),
                document.getImageFileId(),
                document.isActive(),
                document.isFeatured(),
                document.getAttributes(),
                nullSafeDecimal(document.getAvgRating()),
                defaultInteger(document.getTotalReviews()),
                defaultInteger(document.getTotalOrders()),
                toPromotionData(document.getPromotion()),
                toCouponData(document.getCoupon()),
                toDeliveryRuleData(document.getDeliveryRule()),
                Optional.ofNullable(document.getVariants()).orElse(List.of()).stream().map(this::toVariantData).toList(),
                Optional.ofNullable(document.getImages()).orElse(List.of()).stream().map(this::toImageData).toList(),
                document.getUpdatedAt()
        );
    }

    private ShopProductPromotionData toPromotionData(ShopProductView.Promotion document) {
        if (document == null) {
            return null;
        }
        return new ShopProductPromotionData(
                document.getPromotionId(),
                document.getPromotionType(),
                document.getStartsAt(),
                document.getEndsAt(),
                document.getPriorityScore(),
                document.getPaidAmount(),
                document.getStatus()
        );
    }

    private ShopProductCouponData toCouponData(ShopProductView.Coupon document) {
        if (document == null) {
            return null;
        }
        return new ShopProductCouponData(
                document.getCouponId(),
                document.getCouponCode(),
                document.getCouponTitle(),
                document.getDiscountType(),
                document.getDiscountValue(),
                document.getMinOrderAmount(),
                document.getMaxDiscountAmount(),
                document.getStartsAt(),
                document.getEndsAt(),
                document.isActive()
        );
    }

    private ShopProductDeliveryRuleData toDeliveryRuleData(ShopProductView.DeliveryRule document) {
        if (document == null) {
            return null;
        }
        return new ShopProductDeliveryRuleData(
                document.getShopLocationId(),
                document.getDeliveryType(),
                document.getRadiusKm(),
                document.getMinOrderAmount(),
                document.getDeliveryFee(),
                document.getFreeDeliveryAbove(),
                document.getOrderCutoffMinutesBeforeClose(),
                document.getClosingSoonMinutes()
        );
    }

    private ShopProductVariantData toVariantData(ShopProductView.Variant document) {
        return new ShopProductVariantData(
                document.getVariantId(),
                document.getVariantName(),
                document.getColorName(),
                document.getColorHex(),
                document.getUnitValue(),
                document.getUnitType(),
                document.getWeightInGrams(),
                document.getMrp(),
                document.getSellingPrice(),
                defaultInteger(document.getQuantityAvailable()),
                defaultInteger(document.getReservedQuantity()),
                document.getReorderLevel(),
                defaultText(document.getInventoryStatus(), "OUT_OF_STOCK"),
                document.isDefaultVariant(),
                document.isActive(),
                document.getSortOrder(),
                document.getAttributes()
        );
    }

    private ShopProductImageData toImageData(ShopProductView.Image document) {
        return new ShopProductImageData(
                document.getImageId(),
                document.getFileId(),
                document.getImageRole(),
                document.getVariantId(),
                document.getSortOrder(),
                document.isPrimaryImage()
        );
    }

    public ShopOrderData toShopOrderData(ShopOrderView document) {
        return new ShopOrderData(
                document.getOrderId(),
                document.getOrderCode(),
                document.getOrderStatus(),
                document.getPaymentStatus(),
                document.getCustomerName(),
                document.getCustomerPhone(),
                document.getCreatedAt(),
                defaultInteger(document.getItemCount()),
                defaultAmount(document.getItemsTotal()),
                defaultAmount(document.getDeliveryCharges()),
                defaultAmount(document.getPlatformFee()),
                defaultAmount(document.getTotalOrderValue()),
                document.getAddressLabel(),
                document.getAddressLine(),
                document.getDeliveryLatitude(),
                document.getDeliveryLongitude(),
                resolveCancelReason(document),
                document.getCancelledBy(),
                document.getRating(),
                document.getReviewComment(),
                Optional.ofNullable(document.getItems()).orElse(List.of()).stream()
                        .map(item -> new ShopOrderItemData(
                                item.getItemName(),
                                item.getQuantity(),
                                item.getUnitLabel(),
                                defaultAmount(item.getUnitPrice()),
                                defaultAmount(item.getLineTotal())
                        ))
                        .toList(),
                paymentSecondsRemaining(document)
        );
    }

    // Accept-first 5-minute payment window, anchored to the ACCEPTED timeline
    // event, so the shop's countdown matches the customer's and survives reloads.
    private static final long PAYMENT_WINDOW_SECONDS = 5 * 60;

    private Long paymentSecondsRemaining(ShopOrderView document) {
        String status = document.getOrderStatus() == null ? "" : document.getOrderStatus().trim().toUpperCase();
        if (!"ACCEPTED".equals(status) && !"PAYMENT_PENDING".equals(status)) {
            return null;
        }
        if ("PAID".equalsIgnoreCase(document.getPaymentStatus() == null ? "" : document.getPaymentStatus().trim())) {
            return null;
        }
        LocalDateTime acceptedAt = null;
        if (document.getTimeline() != null) {
            for (ShopOrderView.TimelineEvent event : document.getTimeline()) {
                String newStatus = event.getNewStatus() == null ? "" : event.getNewStatus().trim().toUpperCase();
                if ("ACCEPTED".equals(newStatus) && event.getChangedAt() != null) {
                    acceptedAt = event.getChangedAt();
                }
            }
        }
        if (acceptedAt == null) {
            // Fall back to the FIXED creation time, never updatedAt (which is bumped
            // by later writes and would reset/extend the window past the countdown).
            acceptedAt = document.getCreatedAt();
        }
        if (acceptedAt == null) {
            return PAYMENT_WINDOW_SECONDS;
        }
        long elapsed = Duration.between(acceptedAt, LocalDateTime.now()).getSeconds();
        return Math.max(0L, PAYMENT_WINDOW_SECONDS - elapsed);
    }

    // Reason captured in the timeline when the order was cancelled/rejected, so
    // the shop card can show why (and in a distinct colour).
    private String resolveCancelReason(ShopOrderView document) {
        String status = document.getOrderStatus() == null ? "" : document.getOrderStatus().trim().toUpperCase();
        if (!"CANCELLED".equals(status) && !"REJECTED".equals(status)) {
            return null;
        }
        if (document.getTimeline() == null) {
            return null;
        }
        String reason = null;
        for (ShopOrderView.TimelineEvent event : document.getTimeline()) {
            String newStatus = event.getNewStatus() == null ? "" : event.getNewStatus().trim().toUpperCase();
            if (("CANCELLED".equals(newStatus) || "REJECTED".equals(newStatus))
                    && event.getReason() != null && !event.getReason().isBlank()) {
                reason = event.getReason().trim();
            }
        }
        return reason;
    }

    private ShopProductDeliveryRuleData resolveDeliveryRule(Long shopId) {
        return shopDeliveryRuleViewService.refreshPrimaryDeliveryRule(shopId)
                .orElse(null);
    }

    private ShopDashboardMetricData metric(List<ShopOrderData> orders, LocalDateTime start) {
        List<ShopOrderData> filtered = start == null ? orders : orders.stream()
                .filter(order -> order.createdAt() != null && !order.createdAt().isBefore(start))
                .toList();
        // Missed = declined (shop rejected) + not-accepted (auto-rejected on the
        // payment/no-accept timeout) — both land in REJECTED. They are not real
        // orders, so they're excluded from the order/completed/cancelled counts
        // and reported separately as the value the shop missed out on.
        List<ShopOrderData> missed = filtered.stream()
                .filter(order -> "REJECTED".equalsIgnoreCase(order.orderStatus()))
                .toList();
        List<ShopOrderData> realOrders = filtered.stream()
                .filter(order -> !"REJECTED".equalsIgnoreCase(order.orderStatus()))
                .toList();
        long completed = realOrders.stream().filter(order -> "DELIVERED".equalsIgnoreCase(order.orderStatus())).count();
        long cancelled = realOrders.stream().filter(order -> "CANCELLED".equalsIgnoreCase(order.orderStatus())).count();
        BigDecimal orderValue = realOrders.stream()
                .map(ShopOrderData::totalOrderValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Earnings = the shop's NET take on paid, non-cancelled orders only:
        // item subtotal minus the platform commission (platform fee + delivery
        // are not the shop's). Excludes pending/unpaid and cancelled orders.
        BigDecimal earnings = realOrders.stream()
                .filter(this::isEarningOrder)
                .map(order -> shopFeeSettingsService.shopNetEarning(order.itemsTotal()))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal missedValue = missed.stream()
                .map(ShopOrderData::totalOrderValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ShopDashboardMetricData(
                realOrders.size(), completed, cancelled, orderValue, earnings, missed.size(), missedValue);
    }

    // An order contributes to earnings only when it has been paid and is not in
    // a cancelled/rejected terminal state.
    private boolean isEarningOrder(ShopOrderData order) {
        String status = order.orderStatus() == null ? "" : order.orderStatus().trim().toUpperCase();
        String payment = order.paymentStatus() == null ? "" : order.paymentStatus().trim().toUpperCase();
        boolean paid = "PAID".equals(payment) || "PAYMENT_COMPLETED".equals(payment) || "COMPLETED".equals(payment);
        boolean cancelledOrRejected = "CANCELLED".equals(status) || "REJECTED".equals(status);
        return paid && !cancelledOrRejected;
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

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nullSafeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
