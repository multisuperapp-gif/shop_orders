package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface StorefrontCatalogRepository extends Repository<ShopEntity, Long> {
    interface ShopTypeView {
        Long getId();
        String getName();
        String getNormalizedName();
        String getThemeColor();
        Boolean getComingSoon();
        String getComingSoonMessage();
        String getIconObjectKey();
        String getBannerObjectKey();
        Integer getSortOrder();
    }

    interface ShopCategoryView {
        Long getId();
        Long getParentCategoryId();
        Long getShopTypeId();
        String getName();
        String getNormalizedName();
        String getThemeColor();
        Boolean getComingSoon();
        String getComingSoonMessage();
        String getImageObjectKey();
        Integer getSortOrder();
    }

    interface ProductCardView {
        Long getProductId();
        Long getVariantId();
        Long getShopId();
        Long getShopTypeId();
        Long getCategoryId();
        String getProductName();
        String getShopName();
        String getCategoryName();
        String getBrandName();
        String getShortDescription();
        String getProductType();
        BigDecimal getMrp();
        BigDecimal getSellingPrice();
        BigDecimal getAvgRating();
        Long getTotalReviews();
        Long getTotalOrders();
        String getInventoryStatus();
        Boolean getOutOfStock();
        Integer getPromotionScore();
        String getImageObjectKey();
    }

    interface ShopSummaryView {
        Long getShopId();
        Long getShopTypeId();
        String getShopName();
        String getShopCode();
        String getLogoObjectKey();
        String getCoverObjectKey();
        BigDecimal getAvgRating();
        Long getTotalReviews();
        String getCity();
        BigDecimal getLatitude();
        BigDecimal getLongitude();
        String getDeliveryType();
        BigDecimal getRadiusKm();
        BigDecimal getMinOrderAmount();
        BigDecimal getDeliveryFee();
        Boolean getOpenNow();
        Boolean getClosingSoon();
        Boolean getAcceptsOrders();
        String getClosesAt();
    }

    interface ProductBaseView {
        Long getProductId();
        Long getShopId();
        Long getShopTypeId();
        Long getCategoryId();
        String getProductName();
        String getShopName();
        String getCategoryName();
        String getBrandName();
        String getDescription();
        String getShortDescription();
        String getProductType();
        String getAttributesJson();
        BigDecimal getAvgRating();
        Long getTotalReviews();
        Long getTotalOrders();
    }

    interface ProductImageView {
        Long getId();
        String getObjectKey();
        String getImageRole();
        Integer getSortOrder();
        Boolean getPrimary();
    }

    interface ProductVariantView {
        Long getId();
        String getVariantName();
        BigDecimal getMrp();
        BigDecimal getSellingPrice();
        Boolean getDefaultVariant();
        Boolean getActive();
        String getAttributesJson();
        String getInventoryStatus();
        Boolean getOutOfStock();
    }

    interface ProductOptionRowView {
        Long getGroupId();
        String getGroupName();
        String getGroupType();
        Integer getMinSelect();
        Integer getMaxSelect();
        Boolean getRequired();
        Long getOptionId();
        String getOptionName();
        BigDecimal getPriceDelta();
        Boolean getDefaultOption();
    }

    @Query(value = """
            SELECT
                st.id AS id,
                COALESCE(st.display_label, st.name) AS name,
                st.normalized_name AS normalizedName,
                st.theme_color AS themeColor,
                st.is_coming_soon AS comingSoon,
                st.coming_soon_message AS comingSoonMessage,
                icon_file.object_key AS iconObjectKey,
                banner_file.object_key AS bannerObjectKey,
                st.sort_order AS sortOrder
            FROM shop_types st
            LEFT JOIN files icon_file ON icon_file.id = st.icon_file_id
            LEFT JOIN files banner_file ON banner_file.id = st.banner_file_id
            WHERE st.is_active = 1
            ORDER BY st.sort_order ASC, st.name ASC
            """, nativeQuery = true)
    List<ShopTypeView> findActiveShopTypes();

    @Query(value = """
            SELECT
                st.id AS id,
                COALESCE(st.display_label, st.name) AS name,
                st.normalized_name AS normalizedName,
                st.theme_color AS themeColor,
                st.is_coming_soon AS comingSoon,
                st.coming_soon_message AS comingSoonMessage,
                icon_file.object_key AS iconObjectKey,
                banner_file.object_key AS bannerObjectKey,
                st.sort_order AS sortOrder
            FROM shop_types st
            LEFT JOIN files icon_file ON icon_file.id = st.icon_file_id
            LEFT JOIN files banner_file ON banner_file.id = st.banner_file_id
            WHERE st.is_active = 1
              AND st.normalized_name = :normalizedName
            LIMIT 1
            """, nativeQuery = true)
    Optional<ShopTypeView> findActiveShopTypeByNormalizedName(@Param("normalizedName") String normalizedName);

    @Query(value = """
            SELECT
                sc.id AS id,
                sc.parent_category_id AS parentCategoryId,
                stcm.shop_type_id AS shopTypeId,
                COALESCE(sc.display_label, sc.name) AS name,
                sc.normalized_name AS normalizedName,
                sc.theme_color AS themeColor,
                stcm.is_coming_soon AS comingSoon,
                stcm.coming_soon_message AS comingSoonMessage,
                image_file.object_key AS imageObjectKey,
                COALESCE(NULLIF(stcm.sort_order, 0), sc.sort_order) AS sortOrder
            FROM shop_type_category_mappings stcm
            INNER JOIN shop_categories sc ON sc.id = stcm.shop_category_id
            LEFT JOIN files image_file ON image_file.id = sc.image_file_id
            WHERE stcm.is_active = 1
              AND sc.is_active = 1
              AND (:shopTypeId IS NULL OR stcm.shop_type_id = :shopTypeId)
              AND (
                    (:parentCategoryId IS NULL AND sc.parent_category_id IS NULL)
                    OR sc.parent_category_id = :parentCategoryId
              )
            ORDER BY COALESCE(NULLIF(stcm.sort_order, 0), sc.sort_order) ASC, sc.name ASC
            """, nativeQuery = true)
    List<ShopCategoryView> findCategories(
            @Param("shopTypeId") Long shopTypeId,
            @Param("parentCategoryId") Long parentCategoryId
    );

    @Query(value = """
            SELECT
                p.id AS productId,
                pv.id AS variantId,
                s.id AS shopId,
                s.shop_type_id AS shopTypeId,
                sc.id AS categoryId,
                p.name AS productName,
                s.shop_name AS shopName,
                sc.name AS categoryName,
                p.brand_name AS brandName,
                p.short_description AS shortDescription,
                p.product_type AS productType,
                pv.mrp AS mrp,
                pv.selling_price AS sellingPrice,
                p.avg_rating AS avgRating,
                p.total_reviews AS totalReviews,
                p.total_orders AS totalOrders,
                COALESCE(i.inventory_status, 'OUT_OF_STOCK') AS inventoryStatus,
                CASE
                    WHEN COALESCE(i.inventory_status, 'OUT_OF_STOCK') = 'OUT_OF_STOCK'
                      OR COALESCE(i.quantity_available, 0) <= COALESCE(i.reserved_quantity, 0)
                    THEN 1 ELSE 0
                END AS outOfStock,
                COALESCE((
                    SELECT MAX(pp.priority_score)
                    FROM product_promotions pp
                    WHERE pp.product_id = p.id
                      AND pp.status = 'ACTIVE'
                      AND CURRENT_TIMESTAMP BETWEEN pp.starts_at AND pp.ends_at
                ), 0) AS promotionScore,
                image_file.object_key AS imageObjectKey
            FROM products p
            INNER JOIN shops s ON s.id = p.shop_id
            INNER JOIN shop_categories sc ON sc.id = p.shop_category_id
            LEFT JOIN product_variants pv
              ON pv.product_id = p.id
             AND pv.is_active = 1
             AND pv.is_default = 1
            LEFT JOIN inventory i ON i.variant_id = pv.id
            LEFT JOIN product_images pi
              ON pi.product_id = p.id
             AND pi.is_primary = 1
            LEFT JOIN files image_file ON image_file.id = pi.file_id
            WHERE p.is_active = 1
              AND s.approval_status = 'APPROVED'
              AND s.operational_status <> 'INACTIVE'
              AND (:shopTypeId IS NULL OR s.shop_type_id = :shopTypeId)
              AND (:categoryId IS NULL OR p.shop_category_id = :categoryId)
              AND (
                    :search IS NULL
                    OR p.name LIKE :search
                    OR s.shop_name LIKE :search
                    OR sc.name LIKE :search
                    OR p.brand_name LIKE :search
              )
            ORDER BY
              outOfStock ASC,
              promotionScore DESC,
              p.avg_rating DESC,
              p.total_orders DESC,
              p.updated_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ProductCardView> findProducts(
            @Param("shopTypeId") Long shopTypeId,
            @Param("categoryId") Long categoryId,
            @Param("search") String search,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = """
            SELECT
                s.id AS shopId,
                s.shop_type_id AS shopTypeId,
                s.shop_name AS shopName,
                s.shop_code AS shopCode,
                logo_file.object_key AS logoObjectKey,
                cover_file.object_key AS coverObjectKey,
                s.avg_rating AS avgRating,
                s.total_reviews AS totalReviews,
                address.city AS city,
                sl.latitude AS latitude,
                sl.longitude AS longitude,
                sdr.delivery_type AS deliveryType,
                sdr.radius_km AS radiusKm,
                sdr.min_order_amount AS minOrderAmount,
                sdr.delivery_fee AS deliveryFee,
                CASE
                    WHEN soh.is_closed = 1 THEN 0
                    WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time THEN 1
                    ELSE 0
                END AS openNow,
                CASE
                    WHEN soh.is_closed = 1 THEN 0
                    WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time
                     AND CURRENT_TIME() >= SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.closing_soon_minutes, 60) * 60))
                    THEN 1
                    ELSE 0
                END AS closingSoon,
                CASE
                    WHEN soh.is_closed = 1 THEN 0
                    WHEN CURRENT_TIME() BETWEEN soh.open_time AND SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.order_cutoff_minutes_before_close, 30) * 60))
                    THEN 1
                    ELSE 0
                END AS acceptsOrders,
                DATE_FORMAT(soh.close_time, '%H:%i') AS closesAt
            FROM shops s
            LEFT JOIN files logo_file ON logo_file.id = s.logo_file_id
            LEFT JOIN files cover_file ON cover_file.id = s.cover_file_id
            INNER JOIN shop_locations sl ON sl.shop_id = s.id AND sl.is_primary = 1
            INNER JOIN user_addresses address ON address.id = sl.address_id
            LEFT JOIN shop_delivery_rules sdr ON sdr.shop_location_id = sl.id
            LEFT JOIN shop_operating_hours soh
              ON soh.shop_location_id = sl.id
             AND soh.weekday = WEEKDAY(CURRENT_DATE())
            WHERE s.approval_status = 'APPROVED'
              AND s.shop_type_id = :shopTypeId
              AND s.shop_name IS NOT NULL
              AND (
                    :categoryId IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM products p
                        WHERE p.shop_id = s.id
                          AND p.shop_category_id = :categoryId
                          AND p.is_active = 1
                    )
              )
              AND (
                    :search IS NULL
                    OR s.shop_name LIKE :search
                    OR address.city LIKE :search
              )
            ORDER BY
              acceptsOrders DESC,
              closingSoon ASC,
              CASE
                  WHEN :userLatitude IS NOT NULL AND :userLongitude IS NOT NULL THEN
                      6371 * ACOS(
                          LEAST(
                              1,
                              COS(RADIANS(:userLatitude)) * COS(RADIANS(sl.latitude))
                              * COS(RADIANS(sl.longitude) - RADIANS(:userLongitude))
                              + SIN(RADIANS(:userLatitude)) * SIN(RADIANS(sl.latitude))
                          )
                      )
                  ELSE NULL
              END ASC,
              s.avg_rating DESC,
              s.total_reviews DESC,
              s.updated_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ShopSummaryView> findShops(
            @Param("shopTypeId") Long shopTypeId,
            @Param("categoryId") Long categoryId,
            @Param("search") String search,
            @Param("userLatitude") Double userLatitude,
            @Param("userLongitude") Double userLongitude,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = """
            SELECT
                s.id AS shopId,
                s.shop_type_id AS shopTypeId,
                s.shop_name AS shopName,
                s.shop_code AS shopCode,
                logo_file.object_key AS logoObjectKey,
                cover_file.object_key AS coverObjectKey,
                s.avg_rating AS avgRating,
                s.total_reviews AS totalReviews,
                address.city AS city,
                sl.latitude AS latitude,
                sl.longitude AS longitude,
                sdr.delivery_type AS deliveryType,
                sdr.radius_km AS radiusKm,
                sdr.min_order_amount AS minOrderAmount,
                sdr.delivery_fee AS deliveryFee,
                CASE
                    WHEN soh.is_closed = 1 THEN 0
                    WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time THEN 1
                    ELSE 0
                END AS openNow,
                CASE
                    WHEN soh.is_closed = 1 THEN 0
                    WHEN CURRENT_TIME() BETWEEN soh.open_time AND soh.close_time
                     AND CURRENT_TIME() >= SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.closing_soon_minutes, 60) * 60))
                    THEN 1
                    ELSE 0
                END AS closingSoon,
                CASE
                    WHEN soh.is_closed = 1 THEN 0
                    WHEN CURRENT_TIME() BETWEEN soh.open_time AND SUBTIME(soh.close_time, SEC_TO_TIME(COALESCE(sdr.order_cutoff_minutes_before_close, 30) * 60))
                    THEN 1
                    ELSE 0
                END AS acceptsOrders,
                DATE_FORMAT(soh.close_time, '%H:%i') AS closesAt
            FROM shops s
            LEFT JOIN files logo_file ON logo_file.id = s.logo_file_id
            LEFT JOIN files cover_file ON cover_file.id = s.cover_file_id
            INNER JOIN shop_locations sl ON sl.shop_id = s.id AND sl.is_primary = 1
            INNER JOIN user_addresses address ON address.id = sl.address_id
            LEFT JOIN shop_delivery_rules sdr ON sdr.shop_location_id = sl.id
            LEFT JOIN shop_operating_hours soh
              ON soh.shop_location_id = sl.id
             AND soh.weekday = WEEKDAY(CURRENT_DATE())
            WHERE s.id = :shopId
              AND s.shop_type_id = :shopTypeId
              AND s.approval_status = 'APPROVED'
            LIMIT 1
            """, nativeQuery = true)
    Optional<ShopSummaryView> findShopSummary(
            @Param("shopTypeId") Long shopTypeId,
            @Param("shopId") Long shopId
    );

    @Query(value = """
            SELECT
                sc.id AS id,
                sc.parent_category_id AS parentCategoryId,
                :shopTypeId AS shopTypeId,
                COALESCE(sc.display_label, sc.name) AS name,
                sc.normalized_name AS normalizedName,
                sc.theme_color AS themeColor,
                0 AS comingSoon,
                NULL AS comingSoonMessage,
                image_file.object_key AS imageObjectKey,
                sc.sort_order AS sortOrder
            FROM shop_inventory_categories sic
            INNER JOIN shop_categories sc ON sc.id = sic.shop_category_id
            LEFT JOIN files image_file ON image_file.id = sc.image_file_id
            WHERE sic.shop_id = :shopId
              AND sic.is_enabled = 1
              AND sc.is_active = 1
            ORDER BY sc.sort_order ASC, sc.name ASC
            """, nativeQuery = true)
    List<ShopCategoryView> findShopCategories(
            @Param("shopId") Long shopId,
            @Param("shopTypeId") Long shopTypeId
    );

    @Query(value = """
            SELECT
                p.id AS productId,
                pv.id AS variantId,
                s.id AS shopId,
                s.shop_type_id AS shopTypeId,
                sc.id AS categoryId,
                p.name AS productName,
                s.shop_name AS shopName,
                sc.name AS categoryName,
                p.brand_name AS brandName,
                p.short_description AS shortDescription,
                p.product_type AS productType,
                pv.mrp AS mrp,
                pv.selling_price AS sellingPrice,
                p.avg_rating AS avgRating,
                p.total_reviews AS totalReviews,
                p.total_orders AS totalOrders,
                COALESCE(i.inventory_status, 'OUT_OF_STOCK') AS inventoryStatus,
                CASE
                    WHEN COALESCE(i.inventory_status, 'OUT_OF_STOCK') = 'OUT_OF_STOCK'
                      OR COALESCE(i.quantity_available, 0) <= COALESCE(i.reserved_quantity, 0)
                    THEN 1 ELSE 0
                END AS outOfStock,
                COALESCE((
                    SELECT MAX(pp.priority_score)
                    FROM product_promotions pp
                    WHERE pp.product_id = p.id
                      AND pp.status = 'ACTIVE'
                      AND CURRENT_TIMESTAMP BETWEEN pp.starts_at AND pp.ends_at
                ), 0) AS promotionScore,
                image_file.object_key AS imageObjectKey
            FROM products p
            INNER JOIN shops s ON s.id = p.shop_id
            INNER JOIN shop_categories sc ON sc.id = p.shop_category_id
            LEFT JOIN product_variants pv
              ON pv.product_id = p.id
             AND pv.is_active = 1
             AND pv.is_default = 1
            LEFT JOIN inventory i ON i.variant_id = pv.id
            LEFT JOIN product_images pi
              ON pi.product_id = p.id
             AND pi.is_primary = 1
            LEFT JOIN files image_file ON image_file.id = pi.file_id
            WHERE p.is_active = 1
              AND p.shop_id = :shopId
              AND (:categoryId IS NULL OR p.shop_category_id = :categoryId)
              AND (
                    :search IS NULL
                    OR p.name LIKE :search
                    OR sc.name LIKE :search
                    OR p.brand_name LIKE :search
              )
            ORDER BY
              outOfStock ASC,
              promotionScore DESC,
              p.avg_rating DESC,
              p.total_orders DESC,
              p.updated_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ProductCardView> findProductsByShop(
            @Param("shopId") Long shopId,
            @Param("categoryId") Long categoryId,
            @Param("search") String search,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query(value = """
            SELECT
                p.id AS productId,
                s.id AS shopId,
                s.shop_type_id AS shopTypeId,
                sc.id AS categoryId,
                p.name AS productName,
                s.shop_name AS shopName,
                sc.name AS categoryName,
                p.brand_name AS brandName,
                p.description AS description,
                p.short_description AS shortDescription,
                p.product_type AS productType,
                CAST(p.attributes_json AS CHAR) AS attributesJson,
                p.avg_rating AS avgRating,
                p.total_reviews AS totalReviews,
                p.total_orders AS totalOrders
            FROM products p
            INNER JOIN shops s ON s.id = p.shop_id
            INNER JOIN shop_categories sc ON sc.id = p.shop_category_id
            WHERE p.id = :productId
              AND p.is_active = 1
              AND s.approval_status = 'APPROVED'
            LIMIT 1
            """, nativeQuery = true)
    Optional<ProductBaseView> findProductBase(@Param("productId") Long productId);

    @Query(value = """
            SELECT
                pi.id AS id,
                file.object_key AS objectKey,
                pi.image_role AS imageRole,
                pi.sort_order AS sortOrder,
                pi.is_primary AS `primary`
            FROM product_images pi
            LEFT JOIN files file ON file.id = pi.file_id
            WHERE pi.product_id = :productId
            ORDER BY pi.is_primary DESC, pi.sort_order ASC, pi.id ASC
            """, nativeQuery = true)
    List<ProductImageView> findProductImages(@Param("productId") Long productId);

    @Query(value = """
            SELECT
                pv.id AS id,
                pv.variant_name AS variantName,
                pv.mrp AS mrp,
                pv.selling_price AS sellingPrice,
                pv.is_default AS defaultVariant,
                pv.is_active AS active,
                CAST(pv.attributes_json AS CHAR) AS attributesJson,
                COALESCE(i.inventory_status, 'OUT_OF_STOCK') AS inventoryStatus,
                CASE
                    WHEN COALESCE(i.inventory_status, 'OUT_OF_STOCK') = 'OUT_OF_STOCK'
                      OR COALESCE(i.quantity_available, 0) <= COALESCE(i.reserved_quantity, 0)
                    THEN 1 ELSE 0
                END AS outOfStock
            FROM product_variants pv
            LEFT JOIN inventory i ON i.variant_id = pv.id
            WHERE pv.product_id = :productId
            ORDER BY pv.is_default DESC, pv.sort_order ASC, pv.id ASC
            """, nativeQuery = true)
    List<ProductVariantView> findProductVariants(@Param("productId") Long productId);

    @Query(value = """
            SELECT
                pog.id AS groupId,
                pog.group_name AS groupName,
                pog.group_type AS groupType,
                pog.min_select AS minSelect,
                pog.max_select AS maxSelect,
                pog.is_required AS required,
                po.id AS optionId,
                po.option_name AS optionName,
                po.price_delta AS priceDelta,
                po.is_default AS defaultOption
            FROM product_option_groups pog
            LEFT JOIN product_options po
              ON po.option_group_id = pog.id
             AND po.is_active = 1
            WHERE pog.product_id = :productId
              AND pog.is_active = 1
            ORDER BY pog.sort_order ASC, pog.id ASC, po.sort_order ASC, po.id ASC
            """, nativeQuery = true)
    List<ProductOptionRowView> findProductOptionRows(@Param("productId") Long productId);
}
