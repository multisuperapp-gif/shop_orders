package com.msa.shop_orders.provider.shop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.InventoryEntity;
import com.msa.shop_orders.persistence.entity.OrderEntity;
import com.msa.shop_orders.persistence.entity.OrderItemEntity;
import com.msa.shop_orders.persistence.entity.OrderStatusHistoryEntity;
import com.msa.shop_orders.persistence.entity.PaymentEntity;
import com.msa.shop_orders.persistence.entity.ProductCouponRuleEntity;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.persistence.entity.ProductImageEntity;
import com.msa.shop_orders.persistence.entity.ProductPromotionEntity;
import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import com.msa.shop_orders.persistence.entity.RefundEntity;
import com.msa.shop_orders.persistence.entity.UserAddressEntity;
import com.msa.shop_orders.persistence.repository.InventoryRepository;
import com.msa.shop_orders.persistence.repository.OrderItemRepository;
import com.msa.shop_orders.persistence.repository.OrderRepository;
import com.msa.shop_orders.persistence.repository.OrderStatusHistoryRepository;
import com.msa.shop_orders.persistence.repository.PaymentRepository;
import com.msa.shop_orders.persistence.repository.ProductCouponRuleRepository;
import com.msa.shop_orders.persistence.repository.ProductImageRepository;
import com.msa.shop_orders.persistence.repository.ProductPromotionRepository;
import com.msa.shop_orders.persistence.repository.ProductRepository;
import com.msa.shop_orders.persistence.repository.ProductVariantRepository;
import com.msa.shop_orders.persistence.repository.RefundRepository;
import com.msa.shop_orders.persistence.repository.UserAddressRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShopRuntimeViewService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductPromotionRepository productPromotionRepository;
    private final ProductCouponRuleRepository productCouponRuleRepository;
    private final ShopCategoryViewService shopCategoryViewService;
    private final ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final UserAddressRepository userAddressRepository;
    private final ShopProductViewRepository shopProductViewRepository;
    private final ShopOrderViewRepository shopOrderViewRepository;
    private final ShopShellViewRepository shopShellViewRepository;
    private final ObjectMapper objectMapper;
    private final boolean viewStoreEnabled;

    public ShopRuntimeViewService(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            InventoryRepository inventoryRepository,
            ProductImageRepository productImageRepository,
            ProductPromotionRepository productPromotionRepository,
            ProductCouponRuleRepository productCouponRuleRepository,
            ShopCategoryViewService shopCategoryViewService,
            ShopDeliveryRuleViewService shopDeliveryRuleViewService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            UserAddressRepository userAddressRepository,
            ShopProductViewRepository shopProductViewRepository,
            ShopOrderViewRepository shopOrderViewRepository,
            ShopShellViewRepository shopShellViewRepository,
            ObjectMapper objectMapper,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryRepository = inventoryRepository;
        this.productImageRepository = productImageRepository;
        this.productPromotionRepository = productPromotionRepository;
        this.productCouponRuleRepository = productCouponRuleRepository;
        this.shopCategoryViewService = shopCategoryViewService;
        this.shopDeliveryRuleViewService = shopDeliveryRuleViewService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.userAddressRepository = userAddressRepository;
        this.shopProductViewRepository = shopProductViewRepository;
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.shopShellViewRepository = shopShellViewRepository;
        this.objectMapper = objectMapper;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public void syncProductsForShop(ShopShellView shopEntity) {
        if (!viewStoreEnabled) {
            return;
        }
        List<ProductEntity> products = productRepository.findByShopIdOrderByUpdatedAtDesc(shopEntity.getShopId());
        Map<Long, ShopCategoryView> categoriesById = loadProductCategories(shopEntity, products);
        ShopProductDeliveryRuleData deliveryRuleData = resolveDeliveryRule(shopEntity.getShopId());
        List<ShopProductView> documents = buildProductDocuments(products, categoriesById, deliveryRuleData);
        shopProductViewRepository.deleteByShopId(shopEntity.getShopId());
        if (!documents.isEmpty()) {
            shopProductViewRepository.saveAll(documents);
        }
    }

    public void syncProductById(ShopShellView shopEntity, Long productId) {
        if (!viewStoreEnabled || shopEntity == null || shopEntity.getShopId() == null || productId == null) {
            return;
        }
        ProductEntity product = productRepository.findByIdAndShopId(productId, shopEntity.getShopId()).orElse(null);
        if (product == null) {
            shopProductViewRepository.deleteById(productId);
            return;
        }
        Map<Long, ShopCategoryView> categoriesById = loadProductCategories(shopEntity, List.of(product));
        ShopProductDeliveryRuleData deliveryRuleData = resolveDeliveryRule(shopEntity.getShopId());
        List<ShopProductView> documents = buildProductDocuments(List.of(product), categoriesById, deliveryRuleData);
        if (documents.isEmpty()) {
            shopProductViewRepository.deleteById(productId);
            return;
        }
        shopProductViewRepository.save(documents.getFirst());
    }

    public List<ShopProductData> loadProducts(ShopShellView shopEntity, Long categoryId) {
        if (!viewStoreEnabled) {
            return loadProductsFromSql(shopEntity, categoryId);
        }
        List<ShopProductView> documents = categoryId == null
                ? shopProductViewRepository.findByShopIdOrderByUpdatedAtDesc(shopEntity.getShopId())
                : shopProductViewRepository.findByShopIdAndCategoryIdOrderByUpdatedAtDesc(shopEntity.getShopId(), categoryId);
        return documents.stream()
                .map(this::toShopProductData)
                .toList();
    }

    public ShopProductData loadProduct(ShopShellView shopEntity, Long productId) {
        if (!viewStoreEnabled) {
            return loadProductsFromSql(shopEntity, null).stream()
                    .filter(product -> Objects.equals(product.productId(), productId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
        }
        return shopProductViewRepository.findById(productId)
                .filter(document -> Objects.equals(document.getShopId(), shopEntity.getShopId()))
                .map(this::toShopProductData)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND));
    }

    public void syncOrdersForShop(ShopShellView shopEntity) {
        if (!viewStoreEnabled) {
            return;
        }
        List<OrderEntity> orders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getShopId());
        List<ShopOrderView> documents = buildOrderDocuments(orders);
        shopOrderViewRepository.deleteByShopId(shopEntity.getShopId());
        if (!documents.isEmpty()) {
            shopOrderViewRepository.saveAll(documents);
        }
    }

    public void syncOrdersForUser(Long userId) {
        if (!viewStoreEnabled) {
            return;
        }
        List<OrderEntity> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<ShopOrderView> documents = buildOrderDocuments(orders);
        if (!documents.isEmpty()) {
            shopOrderViewRepository.saveAll(documents);
        }
    }

    public void syncOrderById(Long orderId) {
        if (!viewStoreEnabled || orderId == null) {
            return;
        }
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            shopOrderViewRepository.deleteById(orderId);
            return;
        }
        List<ShopOrderView> documents = buildOrderDocuments(List.of(order));
        if (documents.isEmpty()) {
            shopOrderViewRepository.deleteById(orderId);
            return;
        }
        shopOrderViewRepository.save(documents.getFirst());
    }

    public ShopOrderView buildOrderViewById(Long orderId) {
        if (orderId == null) {
            return null;
        }
        return orderRepository.findById(orderId)
                .map(order -> buildOrderDocuments(List.of(order)))
                .filter(documents -> !documents.isEmpty())
                .map(List::getFirst)
                .orElse(null);
    }

    public List<ShopOrderView> loadConsumerOrders(Long userId) {
        if (!viewStoreEnabled) {
            return buildOrderDocuments(orderRepository.findByUserIdOrderByCreatedAtDesc(userId));
        }
        return shopOrderViewRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public ShopOrderView loadConsumerOrder(Long userId, Long orderId) {
        if (!viewStoreEnabled) {
            return orderRepository.findByIdAndUserId(orderId, userId)
                    .map(order -> buildOrderDocuments(List.of(order)))
                    .filter(documents -> !documents.isEmpty())
                    .map(List::getFirst)
                    .orElse(null);
        }
        return shopOrderViewRepository.findById(orderId)
                .filter(candidate -> Objects.equals(candidate.getUserId(), userId))
                .orElse(null);
    }

    public List<ShopOrderData> loadOrders(ShopShellView shopEntity, String dateFilter, String status, LocalDate fromDate, LocalDate toDate) {
        if (!viewStoreEnabled) {
            return filterOrderData(loadOrdersFromSql(shopEntity), dateFilter, status, fromDate, toDate);
        }
        return filterOrderData(
                shopOrderViewRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getShopId()).stream()
                        .map(this::toShopOrderData)
                        .toList(),
                dateFilter,
                status,
                fromDate,
                toDate
        );
    }

    public ShopOrderData loadOrder(ShopShellView shopEntity, Long orderId) {
        if (!viewStoreEnabled) {
            return loadOrdersFromSql(shopEntity).stream()
                    .filter(order -> Objects.equals(order.orderId(), orderId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found for this shop.", HttpStatus.NOT_FOUND));
        }
        return shopOrderViewRepository.findById(orderId)
                .filter(document -> Objects.equals(document.getShopId(), shopEntity.getShopId()))
                .map(this::toShopOrderData)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found for this shop.", HttpStatus.NOT_FOUND));
    }

    public ShopDashboardSummaryData loadSummary(ShopShellView shopEntity) {
        List<ShopOrderData> orders = viewStoreEnabled
                ? shopOrderViewRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getShopId()).stream()
                        .map(this::toShopOrderData)
                        .toList()
                : loadOrdersFromSql(shopEntity);
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

    public List<ShopOrderData> loadLiveOrders(ShopShellView shopEntity) {
        return (viewStoreEnabled
                ? shopOrderViewRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getShopId()).stream()
                        .map(this::toShopOrderData)
                        .toList()
                : loadOrdersFromSql(shopEntity)).stream()
                .filter(order -> !"DELIVERED".equalsIgnoreCase(order.orderStatus()))
                .filter(order -> !"CANCELLED".equalsIgnoreCase(order.orderStatus()))
                .toList();
    }

    public List<ShopInventoryAlertData> loadInventoryAlerts(ShopShellView shopEntity) {
        List<ShopProductData> products = viewStoreEnabled
                ? shopProductViewRepository.findByShopIdOrderByUpdatedAtDesc(shopEntity.getShopId()).stream()
                        .map(this::toShopProductData)
                        .toList()
                : loadProductsFromSql(shopEntity, null);
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

    private List<ShopProductData> loadProductsFromSql(ShopShellView shopEntity, Long categoryId) {
        List<ProductEntity> products = productRepository.findByShopIdOrderByUpdatedAtDesc(shopEntity.getShopId());
        if (categoryId != null) {
            products = products.stream()
                    .filter(product -> Objects.equals(product.getShopCategoryId(), categoryId))
                    .toList();
        }
        Map<Long, ShopCategoryView> categoriesById = loadProductCategories(shopEntity, products);
        ShopProductDeliveryRuleData deliveryRuleData = resolveDeliveryRule(shopEntity.getShopId());
        return buildProductData(products, categoriesById, deliveryRuleData);
    }

    private List<ShopOrderData> loadOrdersFromSql(ShopShellView shopEntity) {
        List<OrderEntity> orders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopEntity.getShopId());
        return buildOrderCards(orders);
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

    private List<ShopProductView> buildProductDocuments(
            List<ProductEntity> products,
            Map<Long, ShopCategoryView> categoriesById,
            ShopProductDeliveryRuleData deliveryRuleData
    ) {
        return buildProductData(products, categoriesById, deliveryRuleData).stream()
                .map(this::toProductDocument)
                .toList();
    }

    private ShopProductView toProductDocument(ShopProductData data) {
        ShopProductView document = new ShopProductView();
        document.setProductId(data.productId());
        document.setShopId(resolveShopIdFromSku(data));
        document.setCategoryId(data.categoryId());
        document.setCategoryName(data.categoryName());
        document.setItemName(data.itemName());
        document.setShortDescription(data.shortDescription());
        document.setDescription(data.description());
        document.setBrandName(data.brandName());
        document.setVariantName(data.variantName());
        document.setUnitValue(data.unitValue());
        document.setUnitType(data.unitType());
        document.setWeightInGrams(data.weightInGrams());
        document.setMrp(data.mrp());
        document.setSellingPrice(data.sellingPrice());
        document.setQuantityAvailable(data.quantityAvailable());
        document.setReservedQuantity(data.reservedQuantity());
        document.setReorderLevel(data.reorderLevel());
        document.setInventoryStatus(data.inventoryStatus());
        document.setImageFileId(data.imageFileId());
        document.setActive(data.active());
        document.setFeatured(data.featured());
        document.setAttributes(data.attributes());
        document.setAvgRating(data.avgRating());
        document.setTotalReviews(data.totalReviews());
        document.setTotalOrders(data.totalOrders());
        document.setPromotion(toPromotionDocument(data.promotion()));
        document.setCoupon(toCouponDocument(data.coupon()));
        document.setDeliveryRule(toDeliveryRuleDocument(data.deliveryRule()));
        document.setVariants(data.variants().stream().map(this::toVariantDocument).toList());
        document.setImages(data.images().stream().map(this::toImageDocument).toList());
        document.setUpdatedAt(data.updatedAt());
        return document;
    }

    private Long resolveShopIdFromSku(ShopProductData data) {
        return productRepository.findById(data.productId())
                .map(ProductEntity::getShopId)
                .orElse(null);
    }

    private ShopProductView.Promotion toPromotionDocument(ShopProductPromotionData data) {
        if (data == null) {
            return null;
        }
        ShopProductView.Promotion document = new ShopProductView.Promotion();
        document.setPromotionId(data.promotionId());
        document.setPromotionType(data.promotionType());
        document.setStartsAt(data.startsAt());
        document.setEndsAt(data.endsAt());
        document.setPriorityScore(data.priorityScore());
        document.setPaidAmount(data.paidAmount());
        document.setStatus(data.status());
        return document;
    }

    private ShopProductView.Coupon toCouponDocument(ShopProductCouponData data) {
        if (data == null) {
            return null;
        }
        ShopProductView.Coupon document = new ShopProductView.Coupon();
        document.setCouponId(data.couponId());
        document.setCouponCode(data.couponCode());
        document.setCouponTitle(data.couponTitle());
        document.setDiscountType(data.discountType());
        document.setDiscountValue(data.discountValue());
        document.setMinOrderAmount(data.minOrderAmount());
        document.setMaxDiscountAmount(data.maxDiscountAmount());
        document.setStartsAt(data.startsAt());
        document.setEndsAt(data.endsAt());
        document.setActive(data.active());
        return document;
    }

    private ShopProductView.DeliveryRule toDeliveryRuleDocument(ShopProductDeliveryRuleData data) {
        if (data == null) {
            return null;
        }
        ShopProductView.DeliveryRule document = new ShopProductView.DeliveryRule();
        document.setShopLocationId(data.shopLocationId());
        document.setDeliveryType(data.deliveryType());
        document.setRadiusKm(data.radiusKm());
        document.setMinOrderAmount(data.minOrderAmount());
        document.setDeliveryFee(data.deliveryFee());
        document.setFreeDeliveryAbove(data.freeDeliveryAbove());
        document.setOrderCutoffMinutesBeforeClose(data.orderCutoffMinutesBeforeClose());
        document.setClosingSoonMinutes(data.closingSoonMinutes());
        return document;
    }

    private ShopProductView.Variant toVariantDocument(ShopProductVariantData data) {
        ShopProductView.Variant document = new ShopProductView.Variant();
        document.setVariantId(data.variantId());
        document.setVariantName(data.variantName());
        document.setColorName(data.colorName());
        document.setColorHex(data.colorHex());
        document.setUnitValue(data.unitValue());
        document.setUnitType(data.unitType());
        document.setWeightInGrams(data.weightInGrams());
        document.setMrp(data.mrp());
        document.setSellingPrice(data.sellingPrice());
        document.setQuantityAvailable(data.quantityAvailable());
        document.setReservedQuantity(data.reservedQuantity());
        document.setReorderLevel(data.reorderLevel());
        document.setInventoryStatus(data.inventoryStatus());
        document.setDefaultVariant(data.defaultVariant());
        document.setActive(data.active());
        document.setSortOrder(data.sortOrder());
        document.setAttributes(data.attributes());
        return document;
    }

    private ShopProductView.Image toImageDocument(ShopProductImageData data) {
        ShopProductView.Image document = new ShopProductView.Image();
        document.setImageId(data.imageId());
        document.setFileId(data.fileId());
        document.setImageRole(data.imageRole());
        document.setVariantId(data.variantId());
        document.setSortOrder(data.sortOrder());
        document.setPrimaryImage(data.primaryImage());
        return document;
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

    private List<ShopOrderView> buildOrderDocuments(List<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        OrderBuildContext context = loadOrderBuildContext(orders);
        return orders.stream()
                .map(order -> toOrderDocument(order, context))
                .toList();
    }

    private ShopOrderView toOrderDocument(OrderEntity order, OrderBuildContext context) {
        List<ShopOrderView.Item> items = context.itemsByOrderId().getOrDefault(order.getId(), List.of()).stream()
                .map(item -> toOrderItemDocument(item, context.variantsById(), context.productsById(), context.primaryImagesByProductId()))
                .toList();
        ShopShellView shop = context.shopsById().get(order.getShopId());
        ShopOrderView document = new ShopOrderView();
        document.setOrderId(order.getId());
        document.setShopId(order.getShopId());
        document.setUserId(order.getUserId());
        document.setOrderCode(order.getOrderCode());
        document.setShopName(shop == null ? null : shop.getShopName());
        document.setOrderStatus(order.getOrderStatus());
        PaymentEntity payment = context.paymentsByOrderId().get(order.getId());
        UserAddressEntity address = context.addressesById().get(order.getAddressId());
        document.setPaymentStatus(payment == null ? order.getPaymentStatus() : payment.getPaymentStatus());
        document.setPaymentCode(payment == null ? null : payment.getPaymentCode());
        document.setFulfillmentType(order.getFulfillmentType());
        document.setAddressLabel(address == null ? null : address.getLabel());
        document.setAddressLine(formatAddress(address));
        RefundEntity refund = payment == null ? null : context.refundsByPaymentId().get(payment.getId());
        document.setRefundPresent(refund != null);
        document.setLatestRefundStatus(refund == null ? null : refund.getRefundStatus());
        document.setRefund(toRefundDocument(refund));
        document.setCreatedAt(order.getCreatedAt());
        document.setUpdatedAt(order.getUpdatedAt());
        document.setItemCount(items.stream()
                .map(ShopOrderView.Item::getQuantity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        document.setSubtotalAmount(defaultAmount(order.getSubtotalAmount()));
        document.setTaxAmount(defaultAmount(order.getTaxAmount()));
        document.setItemsTotal(defaultAmount(order.getSubtotalAmount()));
        document.setDeliveryCharges(defaultAmount(order.getDeliveryFeeAmount()));
        document.setPlatformFee(defaultAmount(order.getPlatformFeeAmount()));
        document.setDiscountAmount(defaultAmount(order.getDiscountAmount()));
        document.setTotalOrderValue(defaultAmount(order.getTotalAmount()));
        document.setCurrencyCode(order.getCurrencyCode());
        document.setCancellable(!"OUT_FOR_DELIVERY".equalsIgnoreCase(order.getOrderStatus())
                && !"DELIVERED".equalsIgnoreCase(order.getOrderStatus())
                && !"CANCELLED".equalsIgnoreCase(order.getOrderStatus()));
        document.setItems(items);
        document.setTimeline(context.timelineByOrderId().getOrDefault(order.getId(), List.of()).stream()
                .map(this::toTimelineDocument)
                .toList());
        return document;
    }

    private ShopOrderView.TimelineEvent toTimelineDocument(OrderStatusHistoryEntity event) {
        ShopOrderView.TimelineEvent document = new ShopOrderView.TimelineEvent();
        document.setOldStatus(event.getOldStatus());
        document.setNewStatus(event.getNewStatus());
        document.setReason(event.getReason());
        document.setChangedAt(event.getChangedAt());
        return document;
    }

    private ShopOrderView.Refund toRefundDocument(RefundEntity refund) {
        if (refund == null) {
            return null;
        }
        ShopOrderView.Refund document = new ShopOrderView.Refund();
        document.setRefundCode(refund.getRefundCode());
        document.setRefundStatus(refund.getRefundStatus());
        document.setRequestedAmount(refund.getRequestedAmount());
        document.setApprovedAmount(refund.getApprovedAmount());
        document.setReason(refund.getReason());
        document.setInitiatedAt(refund.getInitiatedAt());
        document.setCompletedAt(refund.getCompletedAt());
        return document;
    }

    private ShopOrderView.Item toOrderItemDocument(
            OrderItemEntity item,
            Map<Long, ProductVariantEntity> variantsById,
            Map<Long, ProductEntity> productsById,
            Map<Long, ProductImageEntity> primaryImagesByProductId
    ) {
        ProductVariantEntity variant = variantsById.get(item.getVariantId());
        ProductEntity product = variant == null ? null : productsById.get(variant.getProductId());
        ProductImageEntity image = product == null ? null : primaryImagesByProductId.get(product.getId());
        String productName = product == null ? "Item" : product.getName();
        String variantName = variant == null ? null : variant.getVariantName();
        String itemName = productName;
        if (variantName != null
                && !variantName.isBlank()
                && !variantName.equalsIgnoreCase(productName)) {
            itemName = productName + " (" + variantName + ")";
        }
        ShopOrderView.Item document = new ShopOrderView.Item();
        document.setProductId(product == null ? null : product.getId());
        document.setVariantId(item.getVariantId());
        document.setItemName(itemName);
        document.setProductName(productName);
        document.setVariantName(variantName);
        document.setImageFileId(image == null ? null : image.getFileId());
        document.setQuantity(item.getQuantity());
        document.setUnitLabel(formatUnitLabel(variant));
        document.setUnitPrice(defaultAmount(item.getUnitPriceSnapshot()));
        document.setLineTotal(defaultAmount(item.getLineTotal()));
        return document;
    }

    public ShopOrderData toShopOrderData(ShopOrderView document) {
        return new ShopOrderData(
                document.getOrderId(),
                document.getOrderCode(),
                document.getOrderStatus(),
                document.getCreatedAt(),
                defaultInteger(document.getItemCount()),
                defaultAmount(document.getItemsTotal()),
                defaultAmount(document.getDeliveryCharges()),
                defaultAmount(document.getPlatformFee()),
                defaultAmount(document.getTotalOrderValue()),
                Optional.ofNullable(document.getItems()).orElse(List.of()).stream()
                        .map(item -> new ShopOrderItemData(
                                item.getItemName(),
                                item.getQuantity(),
                                item.getUnitLabel(),
                                defaultAmount(item.getUnitPrice()),
                                defaultAmount(item.getLineTotal())
                        ))
                        .toList()
        );
    }

    private Map<Long, ShopCategoryView> loadProductCategories(ShopShellView shopEntity, List<ProductEntity> products) {
        Set<Long> categoryIds = products.stream()
                .map(ProductEntity::getShopCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        return shopCategoryViewService.findEnabledShopCategories(shopEntity.getShopId(), shopEntity.getShopTypeId()).stream()
                .filter(category -> categoryIds.contains(category.getCategoryId()))
                .collect(Collectors.toMap(ShopCategoryView::getCategoryId, Function.identity(), (left, right) -> left));
    }

    private List<ShopProductData> buildProductData(
            List<ProductEntity> products,
            Map<Long, ShopCategoryView> categoriesById,
            ShopProductDeliveryRuleData deliveryRuleData
    ) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = products.stream().map(ProductEntity::getId).toList();
        List<ProductVariantEntity> allVariants = productVariantRepository.findByProductIdIn(productIds);
        Map<Long, List<ProductVariantEntity>> variantsByProductId = allVariants.stream()
                .sorted(Comparator.comparing(ProductVariantEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductVariantEntity::getId))
                .collect(Collectors.groupingBy(ProductVariantEntity::getProductId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, InventoryEntity> inventoryByVariantId = inventoryRepository.findByVariantIdIn(allVariants.stream().map(ProductVariantEntity::getId).toList())
                .stream()
                .collect(Collectors.toMap(InventoryEntity::getVariantId, Function.identity()));
        Map<Long, List<ProductImageEntity>> imagesByProductId = productImageRepository.findByProductIdIn(productIds).stream()
                .sorted(Comparator.comparing(ProductImageEntity::isPrimaryImage).reversed()
                        .thenComparing(ProductImageEntity::getSortOrder)
                        .thenComparing(ProductImageEntity::getId))
                .collect(Collectors.groupingBy(ProductImageEntity::getProductId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, ProductPromotionEntity> promotionByProductId = productPromotionRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductPromotionEntity::getProductId, Function.identity(), this::pickLatestPromotion));
        Map<Long, ProductCouponRuleEntity> couponByProductId = productCouponRuleRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductCouponRuleEntity::getProductId, Function.identity(), this::pickLatestCoupon));

        return products.stream().map(product -> {
            ShopCategoryView productCategoryEntity = categoriesById.get(product.getShopCategoryId());
            List<ProductVariantEntity> variantEntities = variantsByProductId.getOrDefault(product.getId(), List.of());
            ProductVariantEntity defaultVariant = variantEntities.stream()
                    .filter(ProductVariantEntity::isDefaultVariant)
                    .findFirst()
                    .orElseGet(() -> variantEntities.isEmpty() ? null : variantEntities.getFirst());
            InventoryEntity inventoryEntity = defaultVariant == null ? null : inventoryByVariantId.get(defaultVariant.getId());
            List<ProductImageEntity> imageEntities = imagesByProductId.getOrDefault(product.getId(), List.of());
            ProductImageEntity primaryImage = imageEntities.stream()
                    .filter(ProductImageEntity::isPrimaryImage)
                    .findFirst()
                    .orElseGet(() -> imageEntities.isEmpty() ? null : imageEntities.getFirst());
            return new ShopProductData(
                    product.getId(),
                    product.getShopCategoryId(),
                    productCategoryEntity == null ? null : productCategoryEntity.getName(),
                    product.getName(),
                    product.getShortDescription(),
                    product.getDescription(),
                    product.getBrandName(),
                    defaultVariant == null ? null : defaultVariant.getVariantName(),
                    defaultVariant == null ? null : defaultVariant.getUnitValue(),
                    defaultVariant == null ? null : defaultVariant.getUnitType(),
                    defaultVariant == null ? null : defaultVariant.getWeightInGrams(),
                    defaultVariant == null ? null : defaultVariant.getMrp(),
                    defaultVariant == null ? null : defaultVariant.getSellingPrice(),
                    inventoryEntity == null ? 0 : defaultInteger(inventoryEntity.getQuantityAvailable()),
                    inventoryEntity == null ? 0 : defaultInteger(inventoryEntity.getReservedQuantity()),
                    inventoryEntity == null ? null : inventoryEntity.getReorderLevel(),
                    inventoryEntity == null ? "OUT_OF_STOCK" : inventoryEntity.getInventoryStatus(),
                    primaryImage == null ? null : primaryImage.getFileId(),
                    product.isActive(),
                    product.isFeatured(),
                    readJsonMap(product.getAttributesJson()),
                    nullSafeDecimal(product.getAvgRating()),
                    defaultInteger(product.getTotalReviews()),
                    defaultInteger(product.getTotalOrders()),
                    toPromotionData(promotionByProductId.get(product.getId())),
                    toCouponData(couponByProductId.get(product.getId())),
                    deliveryRuleData,
                    toVariantData(variantEntities, inventoryByVariantId),
                    toImageData(imageEntities),
                    product.getUpdatedAt()
            );
        }).toList();
    }

    private List<ShopProductVariantData> toVariantData(
            List<ProductVariantEntity> variants,
            Map<Long, InventoryEntity> inventoryByVariantId
    ) {
        return variants.stream().map(variant -> {
            InventoryEntity inventoryEntity = inventoryByVariantId.get(variant.getId());
            Map<String, Object> attributes = readJsonMap(variant.getAttributesJson());
            return new ShopProductVariantData(
                    variant.getId(),
                    variant.getVariantName(),
                    asString(attributes.get("colorName")),
                    asString(attributes.get("colorHex")),
                    variant.getUnitValue(),
                    variant.getUnitType(),
                    variant.getWeightInGrams(),
                    variant.getMrp(),
                    variant.getSellingPrice(),
                    inventoryEntity == null ? 0 : defaultInteger(inventoryEntity.getQuantityAvailable()),
                    inventoryEntity == null ? 0 : defaultInteger(inventoryEntity.getReservedQuantity()),
                    inventoryEntity == null ? null : inventoryEntity.getReorderLevel(),
                    inventoryEntity == null ? "OUT_OF_STOCK" : inventoryEntity.getInventoryStatus(),
                    variant.isDefaultVariant(),
                    variant.isActive(),
                    variant.getSortOrder(),
                    attributes
            );
        }).toList();
    }

    private List<ShopProductImageData> toImageData(List<ProductImageEntity> images) {
        return images.stream()
                .map(image -> new ShopProductImageData(
                        image.getId(),
                        image.getFileId(),
                        image.getImageRole(),
                        image.getVariantId(),
                        image.getSortOrder(),
                        image.isPrimaryImage()
                ))
                .toList();
    }

    private ShopProductPromotionData toPromotionData(ProductPromotionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ShopProductPromotionData(
                entity.getId(),
                entity.getPromotionType(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getPriorityScore(),
                entity.getPaidAmount(),
                entity.getStatus()
        );
    }

    private ShopProductCouponData toCouponData(ProductCouponRuleEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ShopProductCouponData(
                entity.getId(),
                entity.getCouponCode(),
                entity.getCouponTitle(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getMinOrderAmount(),
                entity.getMaxDiscountAmount(),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.isActive()
        );
    }

    private ShopProductDeliveryRuleData resolveDeliveryRule(Long shopId) {
        return shopDeliveryRuleViewService.refreshPrimaryDeliveryRule(shopId)
                .orElse(null);
    }

    private List<ShopOrderData> buildOrderCards(List<OrderEntity> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        OrderBuildContext context = loadOrderBuildContext(orders);

        return orders.stream().map(order -> {
            List<ShopOrderItemData> itemData = context.itemsByOrderId().getOrDefault(order.getId(), List.of()).stream()
                    .map(item -> toOrderItemData(item, context.variantsById(), context.productsById()))
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

    private OrderBuildContext loadOrderBuildContext(List<OrderEntity> orders) {
        List<Long> orderIds = orders.stream().map(OrderEntity::getId).toList();
        Map<Long, List<OrderItemEntity>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        Collection<Long> variantIds = itemsByOrderId.values().stream()
                .flatMap(List::stream)
                .map(OrderItemEntity::getVariantId)
                .collect(Collectors.toSet());
        Map<Long, ProductVariantEntity> variantsById = variantIds.isEmpty()
                ? Map.of()
                : productVariantRepository.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        Collection<Long> productIds = variantsById.values().stream()
                .map(ProductVariantEntity::getProductId)
                .collect(Collectors.toSet());
        Map<Long, ProductEntity> productsById = productIds.isEmpty()
                ? Map.of()
                : productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        Map<Long, ProductImageEntity> primaryImagesByProductId = productIds.isEmpty()
                ? Map.of()
                : productImageRepository.findByProductIdIn(productIds).stream()
                .sorted(Comparator.comparing(ProductImageEntity::isPrimaryImage).reversed()
                        .thenComparing(ProductImageEntity::getSortOrder)
                        .thenComparing(ProductImageEntity::getId))
                .collect(Collectors.toMap(ProductImageEntity::getProductId, Function.identity(), (left, right) -> left));
        Map<Long, ShopShellView> shopsById = shopShellViewRepository.findAllById(orders.stream()
                        .map(OrderEntity::getShopId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ShopShellView::getShopId, Function.identity()));
        Map<Long, PaymentEntity> paymentsByOrderId = paymentRepository.findByPayableTypeAndPayableIdIn(
                        "SHOP_ORDER",
                        orderIds
                ).stream()
                .collect(Collectors.toMap(
                        PaymentEntity::getPayableId,
                        Function.identity(),
                        (left, right) -> left.getId() >= right.getId() ? left : right
                ));
        Map<Long, RefundEntity> refundsByPaymentId = refundRepository.findByPaymentIdInOrderByIdDesc(
                        paymentsByOrderId.values().stream().map(PaymentEntity::getId).toList()
                ).stream()
                .collect(Collectors.toMap(
                        RefundEntity::getPaymentId,
                        Function.identity(),
                        (left, right) -> left.getId() >= right.getId() ? left : right
                ));
        Map<Long, List<OrderStatusHistoryEntity>> timelineByOrderId = orderStatusHistoryRepository.findByOrderIdInOrderByChangedAtAscIdAsc(orderIds)
                .stream()
                .collect(Collectors.groupingBy(OrderStatusHistoryEntity::getOrderId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, UserAddressEntity> addressesById = userAddressRepository.findAllById(
                        orders.stream()
                                .map(OrderEntity::getAddressId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(UserAddressEntity::getId, Function.identity()));
        return new OrderBuildContext(itemsByOrderId, variantsById, productsById, primaryImagesByProductId, shopsById, paymentsByOrderId, refundsByPaymentId, addressesById, timelineByOrderId);
    }

    private String formatAddress(UserAddressEntity address) {
        if (address == null || address.getAddressLine1() == null || address.getAddressLine1().isBlank()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(address.getAddressLine1());
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

    private ShopOrderItemData toOrderItemData(
            OrderItemEntity item,
            Map<Long, ProductVariantEntity> variantsById,
            Map<Long, ProductEntity> productsById
    ) {
        ProductVariantEntity variant = variantsById.get(item.getVariantId());
        ProductEntity product = variant == null ? null : productsById.get(variant.getProductId());
        String itemName = product == null ? "Item" : product.getName();
        if (variant != null
                && variant.getVariantName() != null
                && !variant.getVariantName().isBlank()
                && !variant.getVariantName().equalsIgnoreCase(itemName)) {
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

    private ShopDashboardMetricData metric(List<ShopOrderData> orders, LocalDateTime start) {
        List<ShopOrderData> filtered = start == null ? orders : orders.stream()
                .filter(order -> order.createdAt() != null && !order.createdAt().isBefore(start))
                .toList();
        long completed = filtered.stream().filter(order -> "DELIVERED".equalsIgnoreCase(order.orderStatus())).count();
        long cancelled = filtered.stream().filter(order -> "CANCELLED".equalsIgnoreCase(order.orderStatus())).count();
        BigDecimal orderValue = filtered.stream()
                .map(ShopOrderData::totalOrderValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ShopDashboardMetricData(filtered.size(), completed, cancelled, orderValue);
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

    private ProductPromotionEntity pickLatestPromotion(ProductPromotionEntity left, ProductPromotionEntity right) {
        return left.getId() > right.getId() ? left : right;
    }

    private ProductCouponRuleEntity pickLatestCoupon(ProductCouponRuleEntity left, ProductCouponRuleEntity right) {
        return left.getId() > right.getId() ? left : right;
    }

    private Map<String, Object> readJsonMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawJson, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
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

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nullSafeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record OrderBuildContext(
            Map<Long, List<OrderItemEntity>> itemsByOrderId,
            Map<Long, ProductVariantEntity> variantsById,
            Map<Long, ProductEntity> productsById,
            Map<Long, ProductImageEntity> primaryImagesByProductId,
            Map<Long, ShopShellView> shopsById,
            Map<Long, PaymentEntity> paymentsByOrderId,
            Map<Long, RefundEntity> refundsByPaymentId,
            Map<Long, UserAddressEntity> addressesById,
            Map<Long, List<OrderStatusHistoryEntity>> timelineByOrderId
    ) {
    }
}
