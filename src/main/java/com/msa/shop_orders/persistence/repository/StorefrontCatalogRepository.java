package com.msa.shop_orders.persistence.repository;

import com.msa.shop_orders.persistence.entity.ShopEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Storefront SQL access. Since the SQL→MongoDB migration, SQL only owns shop
 * IDENTITY (shop_types, shops, shop_locations, shop_documents) plus shared
 * files/user_addresses. All catalog/commerce data (products, categories,
 * delivery rules, operating hours, inventory) lives in MongoDB and is read via
 * the *ViewRepository classes. This repository therefore only queries the SQL
 * identity/location tables; computed fields (open-now, delivery, veg flags) are
 * enriched in StorefrontCatalogService from the MongoDB views.
 */
public interface StorefrontCatalogRepository extends Repository<ShopEntity, Long> {
    interface ShopTypeView {
        Long getId();
        String getName();
        String getNormalizedName();
        String getThemeColor();
        Integer getComingSoon();
        String getComingSoonMessage();
        String getIconObjectKey();
        String getBannerObjectKey();
        Integer getSortOrder();
    }

    interface ShopIdentityLocationView {
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
        String getRestaurantServiceType();
    }

    @Query(value = """
            SELECT
                st.id AS id,
                st.name AS name,
                st.normalized_name AS normalizedName,
                NULL AS themeColor,
                FALSE AS comingSoon,
                NULL AS comingSoonMessage,
                NULL AS iconObjectKey,
                NULL AS bannerObjectKey,
                st.sort_order AS sortOrder
            FROM shop_types st
            WHERE st.is_active = 1
            ORDER BY st.sort_order ASC, st.name ASC
            """, nativeQuery = true)
    List<ShopTypeView> findActiveShopTypes();

    @Query(value = """
            SELECT
                st.id AS id,
                st.name AS name,
                st.normalized_name AS normalizedName,
                NULL AS themeColor,
                FALSE AS comingSoon,
                NULL AS comingSoonMessage,
                NULL AS iconObjectKey,
                NULL AS bannerObjectKey,
                st.sort_order AS sortOrder
            FROM shop_types st
            WHERE st.is_active = 1
              AND st.normalized_name = :normalizedName
            LIMIT 1
            """, nativeQuery = true)
    Optional<ShopTypeView> findActiveShopTypeByNormalizedName(@Param("normalizedName") String normalizedName);

    /**
     * Approved shops of a type with their primary location, sourced purely from
     * SQL identity tables. Delivery/hours/veg fields are enriched from MongoDB.
     */
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
                s.restaurant_service_type AS restaurantServiceType
            FROM shops s
            LEFT JOIN files logo_file ON logo_file.id = s.logo_file_id
            LEFT JOIN files cover_file ON cover_file.id = s.cover_file_id
            INNER JOIN shop_locations sl ON sl.shop_id = s.id AND sl.is_primary = 1
            INNER JOIN user_addresses address ON address.id = sl.address_id
            WHERE s.approval_status = 'APPROVED'
              AND s.shop_type_id = :shopTypeId
              AND s.shop_name IS NOT NULL
            """, nativeQuery = true)
    List<ShopIdentityLocationView> findApprovedShopsByType(@Param("shopTypeId") Long shopTypeId);

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
                s.restaurant_service_type AS restaurantServiceType
            FROM shops s
            LEFT JOIN files logo_file ON logo_file.id = s.logo_file_id
            LEFT JOIN files cover_file ON cover_file.id = s.cover_file_id
            INNER JOIN shop_locations sl ON sl.shop_id = s.id AND sl.is_primary = 1
            INNER JOIN user_addresses address ON address.id = sl.address_id
            WHERE s.id = :shopId
              AND s.shop_type_id = :shopTypeId
              AND s.approval_status = 'APPROVED'
            LIMIT 1
            """, nativeQuery = true)
    Optional<ShopIdentityLocationView> findApprovedShopById(
            @Param("shopTypeId") Long shopTypeId,
            @Param("shopId") Long shopId
    );
}
