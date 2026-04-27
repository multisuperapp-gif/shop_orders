package com.msa.shop_orders.consumer.cart.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "shop_carts")
@Getter
@Setter
public class ConsumerCartView {
    @Id
    private Long userId;
    private Long shopId;
    private String shopName;
    private String currencyCode;
    private String cartContext;
    private LocalDateTime updatedAt;
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        private Long itemId;
        private String lineKey;
        private Long productId;
        private Long variantId;
        private String productName;
        private String variantName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private Long imageFileId;
        private List<Long> selectedOptionIds;
        private List<String> selectedOptionNames;
        private String cookingRequest;
    }
}
