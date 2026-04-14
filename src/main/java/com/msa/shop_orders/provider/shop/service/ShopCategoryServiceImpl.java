package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.ShopCategoryEntity;
import com.msa.shop_orders.persistence.entity.ShopEntity;
import com.msa.shop_orders.persistence.entity.ShopInventoryCategoryEntity;
import com.msa.shop_orders.persistence.entity.ShopTypeCategoryMappingEntity;
import com.msa.shop_orders.persistence.entity.ShopTypeEntity;
import com.msa.shop_orders.persistence.repository.ShopCategoryRepository;
import com.msa.shop_orders.persistence.repository.ShopInventoryCategoryRepository;
import com.msa.shop_orders.persistence.repository.ShopTypeCategoryMappingRepository;
import com.msa.shop_orders.persistence.repository.ShopTypeRepository;
import com.msa.shop_orders.provider.shop.dto.ShopAvailableCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCategoryData;
import com.msa.shop_orders.provider.shop.dto.ShopCreateCategoryRequest;
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
    private final ShopTypeRepository shopTypeRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final ShopTypeCategoryMappingRepository shopTypeCategoryMappingRepository;
    private final ShopInventoryCategoryRepository shopInventoryCategoryRepository;

    public ShopCategoryServiceImpl(
            ShopContextService shopContextService,
            ShopTypeRepository shopTypeRepository,
            ShopCategoryRepository shopCategoryRepository,
            ShopTypeCategoryMappingRepository shopTypeCategoryMappingRepository,
            ShopInventoryCategoryRepository shopInventoryCategoryRepository
    ) {
        this.shopContextService = shopContextService;
        this.shopTypeRepository = shopTypeRepository;
        this.shopCategoryRepository = shopCategoryRepository;
        this.shopTypeCategoryMappingRepository = shopTypeCategoryMappingRepository;
        this.shopInventoryCategoryRepository = shopInventoryCategoryRepository;
    }

    @Override
    public List<ShopAvailableCategoryData> availableCategories() {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ShopTypeEntity shopTypeEntity = requireShopType(shopEntity);
        Set<Long> allowedCategoryIds = shopTypeCategoryMappingRepository.findByShopTypeIdAndActiveTrueOrderByIdAsc(shopTypeEntity.getId()).stream()
                .map(ShopTypeCategoryMappingEntity::getShopCategoryId)
                .collect(Collectors.toSet());
        Set<Long> addedCategoryIds = shopInventoryCategoryRepository.findByShopIdOrderByIdAsc(shopEntity.getId()).stream()
                .filter(ShopInventoryCategoryEntity::isEnabled)
                .map(ShopInventoryCategoryEntity::getShopCategoryId)
                .collect(Collectors.toSet());
        if (allowedCategoryIds.isEmpty()) {
            return List.of();
        }
        return shopCategoryRepository.findAllById(allowedCategoryIds).stream()
                .filter(ShopCategoryEntity::isActive)
                .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                .map(category -> new ShopAvailableCategoryData(category.getId(), category.getName(), addedCategoryIds.contains(category.getId())))
                .toList();
    }

    @Override
    public List<ShopCategoryData> categories() {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        List<ShopInventoryCategoryEntity> inventoryCategories = shopInventoryCategoryRepository.findByShopIdOrderByIdAsc(shopEntity.getId()).stream()
                .filter(ShopInventoryCategoryEntity::isEnabled)
                .toList();
        if (inventoryCategories.isEmpty()) {
            return List.of();
        }
        Map<Long, ShopCategoryEntity> categoriesById = shopCategoryRepository.findAllById(
                        inventoryCategories.stream().map(ShopInventoryCategoryEntity::getShopCategoryId).toList())
                .stream()
                .filter(ShopCategoryEntity::isActive)
                .collect(Collectors.toMap(ShopCategoryEntity::getId, Function.identity()));
        return inventoryCategories.stream()
                .map(mapping -> categoriesById.get(mapping.getShopCategoryId()))
                .filter(Objects::nonNull)
                .map(category -> new ShopCategoryData(category.getId(), category.getName()))
                .toList();
    }

    @Override
    @Transactional
    public ShopCategoryData createCategory(ShopCreateCategoryRequest request) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ShopTypeEntity shopTypeEntity = requireShopType(shopEntity);
        String displayName = normalizeDisplayName(request.name());
        String normalizedName = normalizeKey(displayName);

        ShopCategoryEntity categoryEntity = shopCategoryRepository.findByNormalizedNameIgnoreCase(normalizedName)
                .orElseGet(() -> {
                    ShopCategoryEntity entity = new ShopCategoryEntity();
                    entity.setName(displayName);
                    entity.setNormalizedName(normalizedName);
                    entity.setCreatedByShopId(shopEntity.getId());
                    entity.setActive(true);
                    return shopCategoryRepository.save(entity);
                });

        if (!categoryEntity.isActive()) {
            categoryEntity.setActive(true);
            categoryEntity.setName(displayName);
            categoryEntity = shopCategoryRepository.save(categoryEntity);
        }

        ensureTypeMapping(shopTypeEntity.getId(), categoryEntity.getId());
        if (shopInventoryCategoryRepository.existsByShopIdAndShopCategoryId(shopEntity.getId(), categoryEntity.getId())) {
            throw new BusinessException("CATEGORY_ALREADY_ADDED", "Category is already added for this shop.", HttpStatus.BAD_REQUEST);
        }
        ShopInventoryCategoryEntity inventoryCategoryEntity = new ShopInventoryCategoryEntity();
        inventoryCategoryEntity.setShopId(shopEntity.getId());
        inventoryCategoryEntity.setShopCategoryId(categoryEntity.getId());
        inventoryCategoryEntity.setEnabled(true);
        shopInventoryCategoryRepository.save(inventoryCategoryEntity);
        return new ShopCategoryData(categoryEntity.getId(), categoryEntity.getName());
    }

    @Override
    @Transactional
    public ShopCategoryData addCategory(Long categoryId) {
        ShopEntity shopEntity = shopContextService.currentApprovedShop();
        ShopTypeEntity shopTypeEntity = requireShopType(shopEntity);
        ShopCategoryEntity categoryEntity = shopCategoryRepository.findById(categoryId)
                .filter(ShopCategoryEntity::isActive)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "Shop category not found.", HttpStatus.BAD_REQUEST));
        if (!shopTypeCategoryMappingRepository.existsByShopTypeIdAndShopCategoryIdAndActiveTrue(shopTypeEntity.getId(), categoryId)) {
            throw new BusinessException("CATEGORY_NOT_ALLOWED", "Selected category is not allowed for this shop type.", HttpStatus.BAD_REQUEST);
        }
        if (shopInventoryCategoryRepository.existsByShopIdAndShopCategoryId(shopEntity.getId(), categoryId)) {
            throw new BusinessException("CATEGORY_ALREADY_ADDED", "Category is already added for this shop.", HttpStatus.BAD_REQUEST);
        }
        ShopInventoryCategoryEntity inventoryCategoryEntity = new ShopInventoryCategoryEntity();
        inventoryCategoryEntity.setShopId(shopEntity.getId());
        inventoryCategoryEntity.setShopCategoryId(categoryId);
        inventoryCategoryEntity.setEnabled(true);
        shopInventoryCategoryRepository.save(inventoryCategoryEntity);
        return new ShopCategoryData(categoryEntity.getId(), categoryEntity.getName());
    }

    private ShopTypeEntity requireShopType(ShopEntity shopEntity) {
        ShopTypeEntity linkedShopType = shopEntity.getShopType();
        if (linkedShopType == null || linkedShopType.getId() == null) {
            throw new BusinessException("SHOP_TYPE_NOT_FOUND", "Approved shop type is not configured.", HttpStatus.BAD_REQUEST);
        }
        return shopTypeRepository.findByIdAndActiveTrue(linkedShopType.getId())
                .orElseThrow(() -> new BusinessException("SHOP_TYPE_NOT_FOUND", "Approved shop type is not configured in masters.", HttpStatus.BAD_REQUEST));
    }

    private void ensureTypeMapping(Long shopTypeId, Long shopCategoryId) {
        if (shopTypeCategoryMappingRepository.existsByShopTypeIdAndShopCategoryIdAndActiveTrue(shopTypeId, shopCategoryId)) {
            return;
        }
        ShopTypeCategoryMappingEntity mappingEntity = new ShopTypeCategoryMappingEntity();
        mappingEntity.setShopTypeId(shopTypeId);
        mappingEntity.setShopCategoryId(shopCategoryId);
        mappingEntity.setActive(true);
        shopTypeCategoryMappingRepository.save(mappingEntity);
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
