package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.ShopCategoryEntity;
import com.msa.shop_orders.persistence.entity.ShopInventoryCategoryEntity;
import com.msa.shop_orders.persistence.entity.ShopTypeCategoryMappingEntity;
import com.msa.shop_orders.persistence.repository.ShopCategoryRepository;
import com.msa.shop_orders.persistence.repository.ShopInventoryCategoryRepository;
import com.msa.shop_orders.persistence.repository.ShopTypeCategoryMappingRepository;
import com.msa.shop_orders.provider.shop.dto.ShopAvailableCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCreateCategoryRequest;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShopCategoryServiceImpl implements ShopCategoryService {
    private final ShopContextService shopContextService;
    private final ShopTypeViewService shopTypeViewService;
    private final ShopCategoryViewService shopCategoryViewService;
    private final ShopCategoryRepository shopCategoryRepository;
    private final ShopTypeCategoryMappingRepository shopTypeCategoryMappingRepository;
    private final ShopInventoryCategoryRepository shopInventoryCategoryRepository;

    public ShopCategoryServiceImpl(
            ShopContextService shopContextService,
            ShopTypeViewService shopTypeViewService,
            ShopCategoryViewService shopCategoryViewService,
            ShopCategoryRepository shopCategoryRepository,
            ShopTypeCategoryMappingRepository shopTypeCategoryMappingRepository,
            ShopInventoryCategoryRepository shopInventoryCategoryRepository
    ) {
        this.shopContextService = shopContextService;
        this.shopTypeViewService = shopTypeViewService;
        this.shopCategoryViewService = shopCategoryViewService;
        this.shopCategoryRepository = shopCategoryRepository;
        this.shopTypeCategoryMappingRepository = shopTypeCategoryMappingRepository;
        this.shopInventoryCategoryRepository = shopInventoryCategoryRepository;
    }

    @Override
    public List<ShopAvailableCategoryData> availableCategories() {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        Long shopTypeId = requireShopTypeId(shopEntity);
        List<ShopCategoryView> allowedCategories = shopCategoryViewService.findAllowedTypeCategories(shopTypeId);
        Set<Long> addedCategoryIds = shopCategoryViewService.findEnabledShopCategories(shopEntity.getShopId(), shopTypeId).stream()
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
        return shopCategoryViewService.findEnabledShopCategories(shopEntity.getShopId(), shopTypeId).stream()
                .map(category -> new ShopCategoryData(category.getCategoryId(), category.getName()))
                .toList();
    }

    @Override
    @Transactional
    public ShopCategoryData createCategory(ShopCreateCategoryRequest request) {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        Long shopTypeId = requireShopTypeId(shopEntity);
        String displayName = normalizeDisplayName(request.name());
        String normalizedName = normalizeKey(displayName);

        ShopCategoryEntity categoryEntity = shopCategoryRepository.findByNormalizedNameIgnoreCase(normalizedName)
                .orElseGet(() -> {
                    ShopCategoryEntity entity = new ShopCategoryEntity();
                    entity.setName(displayName);
                    entity.setNormalizedName(normalizedName);
                    entity.setCreatedByShopId(shopEntity.getShopId());
                    entity.setActive(true);
                    return shopCategoryRepository.save(entity);
                });

        if (!categoryEntity.isActive()) {
            categoryEntity.setActive(true);
            categoryEntity.setName(displayName);
            categoryEntity = shopCategoryRepository.save(categoryEntity);
        }

        ensureTypeMapping(shopTypeId, categoryEntity.getId());
        shopCategoryViewService.syncTypeCategory(shopTypeId, categoryEntity);
        if (shopCategoryViewService.isEnabledShopCategory(shopEntity.getShopId(), shopTypeId, categoryEntity.getId())) {
            throw new BusinessException("CATEGORY_ALREADY_ADDED", "Category is already added for this shop.", HttpStatus.BAD_REQUEST);
        }
        ShopInventoryCategoryEntity inventoryCategoryEntity = new ShopInventoryCategoryEntity();
        inventoryCategoryEntity.setShopId(shopEntity.getShopId());
        inventoryCategoryEntity.setShopCategoryId(categoryEntity.getId());
        inventoryCategoryEntity.setEnabled(true);
        shopInventoryCategoryRepository.save(inventoryCategoryEntity);
        shopCategoryViewService.syncShopCategory(shopEntity.getShopId(), shopTypeId, categoryEntity, true);
        return new ShopCategoryData(categoryEntity.getId(), categoryEntity.getName());
    }

    @Override
    @Transactional
    public ShopCategoryData addCategory(Long categoryId) {
        ShopShellView shopEntity = shopContextService.currentApprovedShop();
        Long shopTypeId = requireShopTypeId(shopEntity);
        ShopCategoryView allowedCategory = shopCategoryViewService.findAllowedTypeCategory(shopTypeId, categoryId)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_ALLOWED", "Selected category is not allowed for this shop type.", HttpStatus.BAD_REQUEST));
        ShopCategoryEntity categoryEntity = shopCategoryRepository.findById(categoryId)
                .filter(ShopCategoryEntity::isActive)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Shop category not found.", HttpStatus.BAD_REQUEST));
        if (!allowedCategory.isEnabled()) {
            throw new BusinessException("CATEGORY_NOT_ALLOWED", "Selected category is not allowed for this shop type.", HttpStatus.BAD_REQUEST);
        }
        if (shopCategoryViewService.isEnabledShopCategory(shopEntity.getShopId(), shopTypeId, categoryId)) {
            throw new BusinessException("CATEGORY_ALREADY_ADDED", "Category is already added for this shop.", HttpStatus.BAD_REQUEST);
        }
        ShopInventoryCategoryEntity inventoryCategoryEntity = new ShopInventoryCategoryEntity();
        inventoryCategoryEntity.setShopId(shopEntity.getShopId());
        inventoryCategoryEntity.setShopCategoryId(categoryId);
        inventoryCategoryEntity.setEnabled(true);
        shopInventoryCategoryRepository.save(inventoryCategoryEntity);
        shopCategoryViewService.syncShopCategory(shopEntity.getShopId(), shopTypeId, categoryEntity, true);
        return new ShopCategoryData(categoryEntity.getId(), categoryEntity.getName());
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

    private void ensureTypeMapping(Long shopTypeId, Long shopCategoryId) {
        if (shopCategoryViewService.findAllowedTypeCategory(shopTypeId, shopCategoryId).isPresent()) {
            return;
        }
        ShopTypeCategoryMappingEntity mappingEntity = new ShopTypeCategoryMappingEntity();
        mappingEntity.setShopTypeId(shopTypeId);
        mappingEntity.setShopCategoryId(shopCategoryId);
        mappingEntity.setActive(true);
        ShopTypeCategoryMappingEntity savedMapping = shopTypeCategoryMappingRepository.save(mappingEntity);
        shopCategoryRepository.findById(savedMapping.getShopCategoryId())
                .ifPresent(category -> shopCategoryViewService.syncTypeCategory(savedMapping.getShopTypeId(), category));
    }

    private String normalizeDisplayName(String rawName) {
        String normalized = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new BusinessException("CATEGORY_NAME_REQUIRED", "Category name is required.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
