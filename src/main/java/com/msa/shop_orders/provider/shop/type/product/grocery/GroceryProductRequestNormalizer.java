package com.msa.shop_orders.provider.shop.type.product.grocery;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.provider.shop.dto.ShopCreateProductRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductImageRequest;
import com.msa.shop_orders.provider.shop.dto.ShopProductVariantRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

@Component
public class GroceryProductRequestNormalizer {

    public ShopCreateProductRequest normalize(ShopCreateProductRequest request) {
        if (request == null) {
            throw new BusinessException("PRODUCT_REQUIRED", "Product payload is required.", HttpStatus.BAD_REQUEST);
        }
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        if (request.attributes() != null) {
            attributes.putAll(request.attributes());
        }
        attributes.put("shopTypeFamily", "GROCERY");

        List<ShopProductVariantRequest> variants = request.variants() == null
                ? null
                : request.variants().stream()
                .filter(Objects::nonNull)
                .map(this::normalizeVariant)
                .toList();
        List<ShopProductImageRequest> images = request.images() == null
                ? null
                : request.images().stream().filter(Objects::nonNull).toList();

        return new ShopCreateProductRequest(
                request.categoryId(),
                normalizeRequiredText(request.itemName(), "ITEM_NAME_REQUIRED", "Item name is required."),
                blankToNull(request.shortDescription()),
                blankToNull(request.description()),
                blankToNull(request.brandName()),
                blankToNull(request.productType()),
                false,
                blankToNull(request.variantName()),
                request.unitValue(),
                blankToNull(request.unitType()),
                request.weightInGrams(),
                request.mrp(),
                request.sellingPrice(),
                request.quantityAvailable(),
                request.reorderLevel(),
                request.imageFileId(),
                blankToNull(request.sku()),
                request.active(),
                request.featured(),
                attributes.isEmpty() ? null : attributes,
                variants,
                images,
                request.promotion(),
                request.coupon()
        );
    }

    private ShopProductVariantRequest normalizeVariant(ShopProductVariantRequest variant) {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        if (variant.attributes() != null) {
            attributes.putAll(variant.attributes());
        }
        attributes.put("shopTypeFamily", "GROCERY");
        return new ShopProductVariantRequest(
                variant.variantId(),
                blankToNull(variant.clientKey()),
                blankToNull(variant.variantName()),
                null,
                null,
                variant.unitValue(),
                blankToNull(variant.unitType()),
                variant.weightInGrams(),
                variant.mrp(),
                variant.sellingPrice(),
                variant.quantityAvailable(),
                variant.reorderLevel(),
                variant.defaultVariant(),
                variant.active(),
                variant.sortOrder(),
                attributes.isEmpty() ? null : attributes
        );
    }

    private String normalizeRequiredText(String value, String code, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
