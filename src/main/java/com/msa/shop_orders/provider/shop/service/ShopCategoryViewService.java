package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.persistence.entity.ShopCategoryEntity;
import com.msa.shop_orders.persistence.entity.ShopInventoryCategoryEntity;
import com.msa.shop_orders.persistence.entity.ShopTypeCategoryMappingEntity;
import com.msa.shop_orders.persistence.repository.ShopCategoryRepository;
import com.msa.shop_orders.persistence.repository.ShopInventoryCategoryRepository;
import com.msa.shop_orders.persistence.repository.ShopTypeCategoryMappingRepository;
import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.repository.ShopCategoryViewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShopCategoryViewService {
    private final ShopCategoryViewRepository shopCategoryViewRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final ShopTypeCategoryMappingRepository shopTypeCategoryMappingRepository;
    private final ShopInventoryCategoryRepository shopInventoryCategoryRepository;
    private final boolean viewStoreEnabled;

    public ShopCategoryViewService(
            ShopCategoryViewRepository shopCategoryViewRepository,
            ShopCategoryRepository shopCategoryRepository,
            ShopTypeCategoryMappingRepository shopTypeCategoryMappingRepository,
            ShopInventoryCategoryRepository shopInventoryCategoryRepository,
            @Value("${mongodb.enabled:false}") boolean viewStoreEnabled
    ) {
        this.shopCategoryViewRepository = shopCategoryViewRepository;
        this.shopCategoryRepository = shopCategoryRepository;
        this.shopTypeCategoryMappingRepository = shopTypeCategoryMappingRepository;
        this.shopInventoryCategoryRepository = shopInventoryCategoryRepository;
        this.viewStoreEnabled = viewStoreEnabled;
    }

    public List<ShopCategoryView> findAllowedTypeCategories(Long shopTypeId) {
        if (shopTypeId == null) {
            return List.of();
        }
        if (!viewStoreEnabled) {
            return loadAllowedTypeCategoriesFromSql(shopTypeId);
        }
        ensureTypeCategoriesSeeded(shopTypeId);
        return shopCategoryViewRepository.findByShopTypeIdAndShopIdIsNullAndEnabledTrue(shopTypeId).stream()
                .sorted(categoryComparator())
                .toList();
    }

    public List<ShopCategoryView> findEnabledShopCategories(Long shopId, Long shopTypeId) {
        if (shopId == null) {
            return List.of();
        }
        if (!viewStoreEnabled) {
            return loadShopCategoriesFromSql(shopId, shopTypeId);
        }
        ensureShopCategoriesSeeded(shopId, shopTypeId);
        return shopCategoryViewRepository.findByShopIdAndEnabledTrue(shopId).stream()
                .sorted(categoryComparator())
                .toList();
    }

    public Optional<ShopCategoryView> findEnabledShopCategory(Long shopId, Long shopTypeId, Long categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return findEnabledShopCategories(shopId, shopTypeId).stream()
                .filter(item -> categoryId.equals(item.getCategoryId()))
                .findFirst();
    }

    public Optional<ShopCategoryView> findAllowedTypeCategory(Long shopTypeId, Long categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return findAllowedTypeCategories(shopTypeId).stream()
                .filter(item -> categoryId.equals(item.getCategoryId()))
                .findFirst();
    }

    public boolean isEnabledShopCategory(Long shopId, Long shopTypeId, Long categoryId) {
        return findEnabledShopCategory(shopId, shopTypeId, categoryId).isPresent();
    }

    public void syncTypeCategory(Long shopTypeId, ShopCategoryEntity categoryEntity) {
        if (!viewStoreEnabled || shopTypeId == null || categoryEntity == null) {
            return;
        }
        shopCategoryViewRepository.save(toTypeDocument(shopTypeId, categoryEntity));
    }

    public void syncShopCategory(Long shopId, Long shopTypeId, ShopCategoryEntity categoryEntity, boolean enabled) {
        if (!viewStoreEnabled || shopId == null || categoryEntity == null) {
            return;
        }
        ShopCategoryView document = toShopDocument(shopId, shopTypeId, categoryEntity);
        document.setEnabled(enabled);
        shopCategoryViewRepository.save(document);
    }

    private void ensureTypeCategoriesSeeded(Long shopTypeId) {
        long existing = shopCategoryViewRepository.countByShopTypeIdAndShopIdIsNull(shopTypeId);
        if (existing > 0) {
            return;
        }
        List<ShopCategoryView> documents = loadAllowedTypeCategoriesFromSql(shopTypeId);
        if (!documents.isEmpty()) {
            shopCategoryViewRepository.saveAll(documents);
        }
    }

    private void ensureShopCategoriesSeeded(Long shopId, Long shopTypeId) {
        long existing = shopCategoryViewRepository.countByShopId(shopId);
        if (existing > 0) {
            return;
        }
        List<ShopCategoryView> documents = loadShopCategoriesFromSql(shopId, shopTypeId);
        if (!documents.isEmpty()) {
            shopCategoryViewRepository.saveAll(documents);
        }
    }

    private List<ShopCategoryView> loadAllowedTypeCategoriesFromSql(Long shopTypeId) {
        Set<Long> categoryIds = shopTypeCategoryMappingRepository.findByShopTypeIdAndActiveTrueOrderByIdAsc(shopTypeId).stream()
                .map(ShopTypeCategoryMappingEntity::getShopCategoryId)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return shopCategoryRepository.findAllById(categoryIds).stream()
                .filter(ShopCategoryEntity::isActive)
                .map(category -> toTypeDocument(shopTypeId, category))
                .sorted(categoryComparator())
                .toList();
    }

    private List<ShopCategoryView> loadShopCategoriesFromSql(Long shopId, Long shopTypeId) {
        List<ShopInventoryCategoryEntity> mappings = shopInventoryCategoryRepository.findByShopIdOrderByIdAsc(shopId).stream()
                .filter(ShopInventoryCategoryEntity::isEnabled)
                .toList();
        if (mappings.isEmpty()) {
            return List.of();
        }
        Map<Long, ShopCategoryEntity> categoriesById = shopCategoryRepository.findAllById(
                        mappings.stream().map(ShopInventoryCategoryEntity::getShopCategoryId).toList())
                .stream()
                .filter(ShopCategoryEntity::isActive)
                .collect(Collectors.toMap(ShopCategoryEntity::getId, Function.identity()));
        return mappings.stream()
                .map(mapping -> categoriesById.get(mapping.getShopCategoryId()))
                .filter(java.util.Objects::nonNull)
                .map(category -> toShopDocument(shopId, shopTypeId, category))
                .sorted(categoryComparator())
                .toList();
    }

    private ShopCategoryView toTypeDocument(Long shopTypeId, ShopCategoryEntity categoryEntity) {
        ShopCategoryView document = new ShopCategoryView();
        document.setId("type:" + shopTypeId + ":" + categoryEntity.getId());
        document.setCategoryId(categoryEntity.getId());
        document.setParentCategoryId(null);
        document.setShopTypeId(shopTypeId);
        document.setName(categoryEntity.getName());
        document.setNormalizedName(categoryEntity.getNormalizedName());
        document.setThemeColor(null);
        document.setComingSoon(false);
        document.setComingSoonMessage(null);
        document.setImageObjectKey(null);
        document.setSortOrder(0);
        document.setShopId(null);
        document.setEnabled(true);
        return document;
    }

    private ShopCategoryView toShopDocument(Long shopId, Long shopTypeId, ShopCategoryEntity categoryEntity) {
        ShopCategoryView document = new ShopCategoryView();
        document.setId("shop:" + shopId + ":" + categoryEntity.getId());
        document.setCategoryId(categoryEntity.getId());
        document.setParentCategoryId(null);
        document.setShopTypeId(shopTypeId);
        document.setName(categoryEntity.getName());
        document.setNormalizedName(categoryEntity.getNormalizedName());
        document.setThemeColor(null);
        document.setComingSoon(false);
        document.setComingSoonMessage(null);
        document.setImageObjectKey(null);
        document.setSortOrder(0);
        document.setShopId(shopId);
        document.setEnabled(true);
        return document;
    }

    private Comparator<ShopCategoryView> categoryComparator() {
        return Comparator.comparingInt(ShopCategoryView::getSortOrder)
                .thenComparing(ShopCategoryView::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }
}
