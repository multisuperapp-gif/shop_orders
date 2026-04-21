package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.config.ApplicationProperties;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.persistence.entity.ShopEntity;
import com.msa.shop_orders.persistence.mongo.document.ShopProductActivityDocument;
import com.msa.shop_orders.persistence.mongo.repository.ShopProductActivityRepository;
import com.msa.shop_orders.provider.shop.dto.ShopProductActivityData;
import com.msa.shop_orders.security.AuthenticatedUser;
import com.msa.shop_orders.security.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ShopProductActivityLogService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopProductActivityLogService.class);

    private final ApplicationProperties applicationProperties;
    private final CurrentUserService currentUserService;
    private final ShopProductActivityRepository shopProductActivityRepository;

    public ShopProductActivityLogService(
            ApplicationProperties applicationProperties,
            CurrentUserService currentUserService,
            ShopProductActivityRepository shopProductActivityRepository
    ) {
        this.applicationProperties = applicationProperties;
        this.currentUserService = currentUserService;
        this.shopProductActivityRepository = shopProductActivityRepository;
    }

    public void productCreated(ShopEntity shopEntity, ProductEntity productEntity) {
        record(shopEntity, productEntity, "PRODUCT_CREATED", Map.of());
    }

    public void productUpdated(ShopEntity shopEntity, ProductEntity productEntity) {
        record(shopEntity, productEntity, "PRODUCT_UPDATED", Map.of());
    }

    public void productDuplicated(ShopEntity shopEntity, ProductEntity sourceProduct, ProductEntity duplicateProduct) {
        record(shopEntity, duplicateProduct, "PRODUCT_DUPLICATED", Map.of("sourceProductId", sourceProduct.getId()));
    }

    public void productStatusChanged(ShopEntity shopEntity, ProductEntity productEntity, boolean active) {
        record(shopEntity, productEntity, active ? "PRODUCT_REACTIVATED" : "PRODUCT_ARCHIVED", Map.of("active", active));
    }

    public void mongoProductCreated(ShopEntity shopEntity, Long productId, String productName) {
        record(shopEntity, productId, productName, "PRODUCT_CREATED", Map.of());
    }

    public void mongoProductUpdated(ShopEntity shopEntity, Long productId, String productName) {
        record(shopEntity, productId, productName, "PRODUCT_UPDATED", Map.of());
    }

    public void mongoProductDuplicated(ShopEntity shopEntity, Long sourceProductId, Long duplicateProductId, String productName) {
        record(shopEntity, duplicateProductId, productName, "PRODUCT_DUPLICATED", Map.of("sourceProductId", sourceProductId));
    }

    public void mongoProductStatusChanged(ShopEntity shopEntity, Long productId, String productName, boolean active) {
        record(shopEntity, productId, productName, active ? "PRODUCT_REACTIVATED" : "PRODUCT_ARCHIVED", Map.of("active", active));
    }

    public List<ShopProductActivityData> productActivity(Long shopId, Long productId) {
        if (!isEnabled()) {
            return List.of();
        }
        try {
            return shopProductActivityRepository.findByShopIdAndProductIdOrderByCreatedAtDesc(shopId, productId).stream()
                    .map(this::toData)
                    .toList();
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to load MongoDB product activity shopId={} productId={}", shopId, productId, exception);
            return List.of();
        }
    }

    private void record(ShopEntity shopEntity, ProductEntity productEntity, String eventType, Map<String, Object> details) {
        record(shopEntity, productEntity.getId(), productEntity.getName(), eventType, details);
    }

    private void record(ShopEntity shopEntity, Long productId, String productName, String eventType, Map<String, Object> details) {
        if (!isEnabled()) {
            return;
        }
        try {
            AuthenticatedUser currentUser = currentUserService.currentUser();
            ShopProductActivityDocument document = new ShopProductActivityDocument();
            document.setShopId(shopEntity.getId());
            document.setProductId(productId);
            document.setActorUserId(currentUser.userId());
            document.setActorPublicUserId(currentUser.publicUserId());
            document.setEventType(eventType);
            document.setProductName(productName);
            document.setDetails(details);
            document.setCreatedAt(LocalDateTime.now());
            shopProductActivityRepository.save(document);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to persist MongoDB product activity event type={} productId={}", eventType, productId, exception);
        }
    }

    private boolean isEnabled() {
        return applicationProperties.mongodb() != null && applicationProperties.mongodb().enabled();
    }

    private ShopProductActivityData toData(ShopProductActivityDocument document) {
        return new ShopProductActivityData(
                document.getId(),
                document.getShopId(),
                document.getProductId(),
                document.getActorUserId(),
                document.getActorPublicUserId(),
                document.getEventType(),
                document.getProductName(),
                document.getDetails(),
                document.getCreatedAt()
        );
    }
}
