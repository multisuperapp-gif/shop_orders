package com.msa.shop_orders.provider.shop.view;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "shop_operating_hours")
@Getter
@Setter
public class ShopOperatingHoursView {
    @Id
    private String id;
    private Long shopId;
    private Long shopLocationId;
    private Integer weekday;
    private boolean closed;
    private String openTime;
    private String closeTime;
}
