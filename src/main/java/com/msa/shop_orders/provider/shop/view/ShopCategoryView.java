package com.msa.shop_orders.provider.shop.view;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "shop_categories")
@Getter
@Setter
public class ShopCategoryView {
    @Id
    private String id;
    private Long categoryId;
    private Long parentCategoryId;
    private Long shopTypeId;
    private String name;
    private String normalizedName;
    private String themeColor;
    private boolean comingSoon;
    private String comingSoonMessage;
    private String imageObjectKey;
    private int sortOrder;
    private Long shopId;
    private boolean enabled;
}
