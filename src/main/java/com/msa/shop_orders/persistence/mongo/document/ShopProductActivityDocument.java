package com.msa.shop_orders.persistence.mongo.document;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "shop_product_activity")
@CompoundIndex(name = "shop_product_activity_shop_product_created_idx", def = "{'shopId': 1, 'productId': 1, 'createdAt': -1}")
@Getter
@Setter
public class ShopProductActivityDocument {
    @Id
    private String id;

    @Indexed
    private Long shopId;

    @Indexed
    private Long productId;

    private Long actorUserId;
    private String actorPublicUserId;
    private String eventType;
    private String productName;
    private Map<String, Object> details;
    private LocalDateTime createdAt;
}
