package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.view.ShopCategoryView;
import com.msa.shop_orders.provider.shop.view.repository.ShopCategoryViewRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ShopCategoryViewService {
    private final ShopCategoryViewRepository shopCategoryViewRepository;

    public ShopCategoryViewService(
            ShopCategoryViewRepository shopCategoryViewRepository
    ) {
        this.shopCategoryViewRepository = shopCategoryViewRepository;
    }

    public List<ShopCategoryView> findAllowedTypeCategories(Long shopTypeId) {
        if (shopTypeId == null) {
            return List.of();
        }
        return shopCategoryViewRepository.findByShopTypeIdAndShopIdIsNullAndEnabledTrue(shopTypeId).stream()
                .sorted(categoryComparator())
                .toList();
    }

    public List<ShopCategoryView> findEnabledShopCategories(Long shopId, Long shopTypeId) {
        return findShopCategories(shopId, shopTypeId).stream()
                .filter(ShopCategoryView::isEnabled)
                .toList();
    }

    public List<ShopCategoryView> findShopCategories(Long shopId, Long shopTypeId) {
        if (shopId == null) {
            return List.of();
        }
        return shopCategoryViewRepository.findByShopId(shopId).stream()
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

    public Optional<ShopCategoryView> findShopCategory(Long shopId, Long shopTypeId, Long categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return shopCategoryViewRepository.findByShopIdAndCategoryId(shopId, categoryId);
    }

    public Optional<ShopCategoryView> findAllowedTypeCategory(Long shopTypeId, Long categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return shopCategoryViewRepository.findByShopTypeIdAndShopIdIsNullAndCategoryId(shopTypeId, categoryId);
    }

    public Optional<ShopCategoryView> findTypeCategoryByNormalizedName(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return Optional.empty();
        }
        return shopCategoryViewRepository.findFirstByShopIdIsNullAndNormalizedNameIgnoreCase(normalizedName);
    }

    public boolean isEnabledShopCategory(Long shopId, Long shopTypeId, Long categoryId) {
        return findEnabledShopCategory(shopId, shopTypeId, categoryId).isPresent();
    }

    public void upsertTypeCategory(Long shopTypeId, Long categoryId, String name, String normalizedName, boolean enabled) {
        if (shopTypeId == null || categoryId == null) {
            return;
        }
        ShopCategoryView document = shopCategoryViewRepository
                .findByShopTypeIdAndShopIdIsNullAndCategoryId(shopTypeId, categoryId)
                .orElseGet(ShopCategoryView::new);
        populate(document, "type:" + shopTypeId + ":" + categoryId, categoryId, shopTypeId, null, name, normalizedName, enabled);
        shopCategoryViewRepository.save(document);
    }

    public void upsertShopCategory(Long shopId, Long shopTypeId, Long categoryId, String name, String normalizedName, boolean enabled) {
        if (shopId == null || categoryId == null) {
            return;
        }
        ShopCategoryView document = shopCategoryViewRepository.findByShopIdAndCategoryId(shopId, categoryId)
                .orElseGet(ShopCategoryView::new);
        populate(document, "shop:" + shopId + ":" + categoryId, categoryId, shopTypeId, shopId, name, normalizedName, enabled);
        shopCategoryViewRepository.save(document);
    }

    private void populate(ShopCategoryView document, String id, Long categoryId, Long shopTypeId, Long shopId, String name, String normalizedName, boolean enabled) {
        document.setId(id);
        document.setCategoryId(categoryId);
        document.setParentCategoryId(null);
        document.setShopTypeId(shopTypeId);
        document.setName(name);
        document.setNormalizedName(normalizedName);
        document.setThemeColor(null);
        document.setComingSoon(false);
        document.setComingSoonMessage(null);
        document.setImageObjectKey(null);
        document.setSortOrder(0);
        document.setShopId(shopId);
        document.setEnabled(enabled);
    }

    private Comparator<ShopCategoryView> categoryComparator() {
        return Comparator.comparingInt(ShopCategoryView::getSortOrder)
                .thenComparing(ShopCategoryView::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }
}
