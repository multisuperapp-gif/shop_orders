package com.msa.shop_orders.consumer.shop.type.shared;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.shoptype.ShopTypeFamily;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopCategoryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopDetailData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopSummaryData;
import com.msa.shop_orders.consumer.shop.dto.ConsumerShopTypeData;
import com.msa.shop_orders.consumer.shop.type.ConsumerShopTypeHandler;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.persistence.repository.ProductRepository;
import com.msa.shop_orders.provider.shop.dto.ShopProductData;
import com.msa.shop_orders.provider.shop.service.ShopCategoryViewService;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.provider.shop.service.ShopTypeViewService;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.ShopTypeView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class SharedConsumerShopTypeHandler implements ConsumerShopTypeHandler {
    private final ShopShellViewRepository shopShellViewRepository;
    private final ProductRepository productRepository;
    private final ShopTypeViewService shopTypeViewService;
    private final ShopCategoryViewService shopCategoryViewService;
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final boolean viewStoreEnabled;

    public SharedConsumerShopTypeHandler(
            ShopShellViewRepository shopShellViewRepository,
            ProductRepository productRepository,
            ShopTypeViewService shopTypeViewService,
            ShopCategoryViewService shopCategoryViewService,
            ShopRuntimeViewService shopRuntimeViewService,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopShellViewRepository = shopShellViewRepository;
        this.productRepository = productRepository;
        this.shopTypeViewService = shopTypeViewService;
        this.shopCategoryViewService = shopCategoryViewService;
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    @Override
    public ShopTypeFamily family() {
        return ShopTypeFamily.SHARED;
    }

    @Override
    public List<ConsumerShopTypeData> shopTypes() {
        return shopTypeViewService.findActiveTypes().stream().map(this::toTypeData).toList();
    }

    @Override
    public List<ConsumerShopSummaryData> shops(Long shopTypeId, String search) {
        String normalizedSearch = normalizeSearch(search);
        return loadStorefrontShells(shopTypeId, normalizedSearch).stream().map(this::toShopSummary).toList();
    }

    @Override
    public ConsumerShopDetailData shopDetail(Long shopId) {
        ShopShellView shell = requireApprovedShop(shopId);
        List<ConsumerShopCategoryData> categories = shopCategoryViewService.findEnabledShopCategories(shell.getShopId(), shell.getShopTypeId()).stream()
                .map(this::toCategoryData)
                .toList();
        return new ConsumerShopDetailData(
                shell.getShopId(),
                shell.getShopCode(),
                shell.getShopTypeId(),
                shell.getShopName(),
                shell.getApprovalStatus(),
                shell.getOperationalStatus(),
                shell.getAvgRating(),
                shell.getTotalReviews(),
                categories
        );
    }

    @Override
    public List<ConsumerShopCategoryData> shopCategories(Long shopId) {
        ShopShellView shop = requireApprovedShop(shopId);
        return shopCategoryViewService.findEnabledShopCategories(shop.getShopId(), shop.getShopTypeId()).stream()
                .map(this::toCategoryData)
                .toList();
    }

    @Override
    public List<ShopProductData> shopProducts(Long shopId, Long categoryId) {
        ShopShellView shop = requireApprovedShop(shopId);
        return shopRuntimeViewService.loadProducts(shop, categoryId).stream().filter(ShopProductData::active).toList();
    }

    @Override
    public ShopProductData shopProductDetail(Long shopId, Long productId) {
        ShopShellView shop = requireApprovedShop(shopId);
        ProductEntity productEntity = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found.", HttpStatus.NOT_FOUND));
        if (!Objects.equals(productEntity.getShopId(), shopId)) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found for this shop.", HttpStatus.NOT_FOUND);
        }
        ShopProductData data = shopRuntimeViewService.loadProduct(shop, productId);
        if (!data.active()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product not found.", HttpStatus.NOT_FOUND);
        }
        return data;
    }

    private ShopShellView requireApprovedShop(Long shopId) {
        ShopShellView shop = shopShellViewRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Shop not found.", HttpStatus.NOT_FOUND));
        if (!isApprovedShop(shop)) {
            throw new BusinessException("SHOP_NOT_FOUND", "Shop not found.", HttpStatus.NOT_FOUND);
        }
        return shop;
    }

    private boolean isApprovedShop(ShopShellView shop) {
        return shop != null && "APPROVED".equalsIgnoreCase(shop.getApprovalStatus());
    }

    private List<ShopShellView> loadStorefrontShells(Long shopTypeId, String search) {
        if (!viewStoreEnabled) {
            return List.of();
        }
        List<ShopShellView> shells = shopTypeId == null
                ? shopShellViewRepository.findByApprovalStatus("APPROVED")
                : shopShellViewRepository.findByApprovalStatusAndShopTypeId("APPROVED", shopTypeId);
        return shells.stream()
                .filter(shell -> search == null || containsIgnoreCase(shell.getShopName(), search) || containsIgnoreCase(shell.getShopCode(), search))
                .limit(200)
                .toList();
    }

    private ConsumerShopTypeData toTypeData(ShopTypeView document) {
        return new ConsumerShopTypeData(document.getId(), document.getName(), document.getSortOrder());
    }

    private ConsumerShopSummaryData toShopSummary(ShopShellView shell) {
        return new ConsumerShopSummaryData(
                shell.getShopId(),
                shell.getShopCode(),
                shell.getShopTypeId(),
                shell.getShopName(),
                shell.getApprovalStatus(),
                shell.getOperationalStatus(),
                shell.getAvgRating(),
                shell.getTotalReviews()
        );
    }

    private ConsumerShopCategoryData toCategoryData(ShopCategoryView document) {
        return new ConsumerShopCategoryData(
                document.getCategoryId(),
                document.getShopTypeId(),
                document.getName(),
                document.getNormalizedName(),
                document.getSortOrder()
        );
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }
}
