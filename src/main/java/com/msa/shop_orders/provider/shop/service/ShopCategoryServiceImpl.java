package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.common.mongo.MongoSequenceService;
import com.msa.shop_orders.provider.shop.dto.ShopAvailableCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCategoryOrderUpdateRequest;
import com.msa.shop_orders.provider.shop.dto.ShopCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCreateCategoryRequest;
import com.msa.shop_orders.provider.shop.dto.ShopCategoryStatusUpdateRequest;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShopCategoryServiceImpl implements ShopCategoryService {
    private final ShopContextService shopContextService;
    private final ShopTypeViewService shopTypeViewService;
    private final ShopCategoryViewService shopCategoryViewService;
    private final MongoSequenceService mongoSequenceService;

    public ShopCategoryServiceImpl(
            ShopContextService shopContextService,
            ShopTypeViewService shopTypeViewService,
            ShopCategoryViewService shopCategoryViewService,
            MongoSequenceService mongoSequenceService
    ) {
        this.shopContextService = shopContextService;
        this.shopTypeViewService = shopTypeViewService;
        this.shopCategoryViewService = shopCategoryViewService;
        this.mongoSequenceService = mongoSequenceService;
    }

    @Override
    public List<ShopAvailableCategoryData> availableCategories() {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        Long shopTypeId = requireShopTypeId(shopEntity);
        List<ShopCategoryView> allowedCategories = shopCategoryViewService.findAllowedTypeCategories(shopTypeId);
        Set<Long> addedCategoryIds = shopCategoryViewService.findShopCategories(shopEntity.getShopId(), shopTypeId).stream()
                .map(ShopCategoryView::getCategoryId)
                .collect(Collectors.toSet());
        if (allowedCategories.isEmpty()) {
            return List.of();
        }
        return allowedCategories.stream()
                .map(category -> new ShopAvailableCategoryData(category.getCategoryId(), category.getName(), addedCategoryIds.contains(category.getCategoryId())))
                .toList();
    }

    @Override
    public List<ShopCategoryData> categories() {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        Long shopTypeId = requireShopTypeId(shopEntity);
        return shopCategoryViewService.findShopCategories(shopEntity.getShopId(), shopTypeId).stream()
                .map(this::toCategoryData)
                .toList();
    }

    @Override
    @Transactional
    public ShopCategoryData createCategory(ShopCreateCategoryRequest request) {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        Long shopTypeId = requireShopTypeId(shopEntity);
        String displayName = normalizeDisplayName(request.name());
        String normalizedName = normalizeKey(displayName);

        ShopCategoryView typeCategory = shopCategoryViewService.findTypeCategoryByNormalizedName(normalizedName).orElse(null);
        Long categoryId = typeCategory == null ? mongoSequenceService.nextValue("shop-category-id") : typeCategory.getCategoryId();
        if (shopCategoryViewService.findShopCategory(shopEntity.getShopId(), shopTypeId, categoryId).isPresent()) {
            throw new BusinessException("CATEGORY_ALREADY_ADDED", "Category is already added for this shop.", HttpStatus.BAD_REQUEST);
        }
        shopCategoryViewService.upsertTypeCategory(shopTypeId, categoryId, displayName, normalizedName, true);
        int nextSortOrder = nextShopCategorySortOrder(shopEntity.getShopId(), shopTypeId);
        shopCategoryViewService.upsertShopCategory(shopEntity.getShopId(), shopTypeId, categoryId, displayName, normalizedName, true, nextSortOrder);
        return new ShopCategoryData(categoryId, displayName, true, nextSortOrder);
    }

    @Override
    @Transactional
    public ShopCategoryData addCategory(Long categoryId) {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        Long shopTypeId = requireShopTypeId(shopEntity);
        ShopCategoryView allowedCategory = shopCategoryViewService.findAllowedTypeCategory(shopTypeId, categoryId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_ALLOWED", "Selected category is not allowed for this shop type.", HttpStatus.BAD_REQUEST));
        if (!allowedCategory.isEnabled()) {
            throw new BusinessException("CATEGORY_NOT_ALLOWED", "Selected category is not allowed for this shop type.", HttpStatus.BAD_REQUEST);
        }
        if (shopCategoryViewService.findShopCategory(shopEntity.getShopId(), shopTypeId, categoryId).isPresent()) {
            throw new BusinessException("CATEGORY_ALREADY_ADDED", "Category is already added for this shop.", HttpStatus.BAD_REQUEST);
        }
        int nextSortOrder = nextShopCategorySortOrder(shopEntity.getShopId(), shopTypeId);
        shopCategoryViewService.upsertShopCategory(
                shopEntity.getShopId(),
                shopTypeId,
                allowedCategory.getCategoryId(),
                allowedCategory.getName(),
                allowedCategory.getNormalizedName(),
                true,
                nextSortOrder
        );
        return new ShopCategoryData(
                allowedCategory.getCategoryId(),
                allowedCategory.getName(),
                true,
                nextSortOrder
        );
    }

    @Override
    @Transactional
    public ShopCategoryData updateCategoryStatus(Long categoryId, ShopCategoryStatusUpdateRequest request) {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        Long shopTypeId = requireShopTypeId(shopEntity);
        boolean nextEnabled = Boolean.TRUE.equals(request.enabled());
        ShopCategoryView category = shopCategoryViewService.findShopCategory(shopEntity.getShopId(), shopTypeId, categoryId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Shop category not found.", HttpStatus.BAD_REQUEST));
        shopCategoryViewService.upsertShopCategory(
                shopEntity.getShopId(),
                shopTypeId,
                category.getCategoryId(),
                category.getName(),
                category.getNormalizedName(),
                nextEnabled,
                category.getSortOrder()
        );
        return new ShopCategoryData(category.getCategoryId(), category.getName(), nextEnabled, category.getSortOrder());
    }

    @Override
    @Transactional
    public List<ShopCategoryData> updateCategoryOrder(ShopCategoryOrderUpdateRequest request) {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        Long shopTypeId = requireShopTypeId(shopEntity);
        List<ShopCategoryView> existing = shopCategoryViewService.findShopCategories(shopEntity.getShopId(), shopTypeId);
        if (existing.isEmpty()) {
            return List.of();
        }
        List<Long> orderedIds = request.categoryIds() == null
                ? List.of()
                : request.categoryIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        Set<Long> existingIds = existing.stream().map(ShopCategoryView::getCategoryId).collect(Collectors.toSet());
        if (orderedIds.size() != existing.size() || !existingIds.equals(Set.copyOf(orderedIds))) {
            throw new BusinessException("CATEGORY_ORDER_INVALID", "Category order payload is invalid.", HttpStatus.BAD_REQUEST);
        }
        Map<Long, ShopCategoryView> categoriesById = existing.stream()
                .collect(Collectors.toMap(ShopCategoryView::getCategoryId, item -> item, (left, right) -> left, LinkedHashMap::new));
        for (int index = 0; index < orderedIds.size(); index++) {
            ShopCategoryView category = categoriesById.get(orderedIds.get(index));
            if (category != null) {
                category.setSortOrder(index);
            }
        }
        shopCategoryViewService.saveAllShopCategories(existing);
        return shopCategoryViewService.findShopCategories(shopEntity.getShopId(), shopTypeId).stream()
                .map(this::toCategoryData)
                .toList();
    }

    private Long requireShopTypeId(ShopShellView shopEntity) {
        Long shopTypeId = shopEntity.getShopTypeId();
        if (shopTypeId == null) {
            throw new BusinessException("SHOP_TYPE_NOT_FOUND", "Approved shop type is not configured.", HttpStatus.BAD_REQUEST);
        }
        if (!shopTypeViewService.isActiveType(shopTypeId)) {
            throw new BusinessException("SHOP_TYPE_NOT_FOUND", "Approved shop type is not configured in masters.", HttpStatus.BAD_REQUEST);
        }
        return shopTypeId;
    }

    private String normalizeDisplayName(String rawName) {
        String normalized = rawName == null ? "" : rawName.trim().replaceAll("\s+", " ");
        if (normalized.isBlank()) {
            throw new BusinessException("CATEGORY_NAME_REQUIRED", "Category name is required.", HttpStatus.BAD_REQUEST);
        }
        return java.util.Arrays.stream(normalized.split(" "))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT)
                        + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().replaceAll("\s+", " ").toUpperCase(Locale.ROOT);
    }

    private int nextShopCategorySortOrder(Long shopId, Long shopTypeId) {
        return shopCategoryViewService.findShopCategories(shopId, shopTypeId).stream()
                .mapToInt(ShopCategoryView::getSortOrder)
                .max()
                .orElse(-1) + 1;
    }

    private ShopCategoryData toCategoryData(ShopCategoryView category) {
        return new ShopCategoryData(
                category.getCategoryId(),
                category.getName(),
                category.isEnabled(),
                category.getSortOrder()
        );
    }
}
