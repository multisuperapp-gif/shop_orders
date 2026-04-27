package com.msa.shop_orders.provider.shop.view;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "shop_types")
@Getter
@Setter
public class ShopTypeView {
    @Id
    private Long id;
    private String name;
    private Integer sortOrder;
    private boolean active;
}
