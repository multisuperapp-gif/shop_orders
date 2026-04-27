package com.msa.shop_orders.provider.shop.view;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "shop_delivery_rules")
@Getter
@Setter
public class ShopDeliveryRuleView {
    @Id
    private Long shopId;
    private Long shopLocationId;
    private String deliveryType;
    private BigDecimal radiusKm;
    private BigDecimal minOrderAmount;
    private BigDecimal deliveryFee;
    private BigDecimal freeDeliveryAbove;
    private Integer orderCutoffMinutesBeforeClose;
    private Integer closingSoonMinutes;
}
