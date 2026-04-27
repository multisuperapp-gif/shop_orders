package com.msa.shop_orders.provider.shop.view;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "shop_orders")
@CompoundIndex(name = "idx_shop_orders_shop_created", def = "{'shopId': 1, 'createdAt': -1}")
@CompoundIndex(name = "idx_shop_orders_user_created", def = "{'userId': 1, 'createdAt': -1}")
@Getter
@Setter
public class ShopOrderView {
    @Id
    private Long orderId;

    @Indexed
    private Long shopId;

    @Indexed
    private Long userId;

    @Indexed
    private String orderStatus;

    private String orderCode;
    private String shopName;
    private String paymentStatus;
    private String paymentCode;
    private String fulfillmentType;
    private String addressLabel;
    private String addressLine;
    private boolean refundPresent;
    private String latestRefundStatus;
    private Refund refund;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer itemCount;
    private BigDecimal subtotalAmount;
    private BigDecimal taxAmount;
    private BigDecimal itemsTotal;
    private BigDecimal deliveryCharges;
    private BigDecimal platformFee;
    private BigDecimal discountAmount;
    private BigDecimal totalOrderValue;
    private String currencyCode;
    private Boolean cancellable;
    private List<Item> items;
    private List<TimelineEvent> timeline;

    @Getter
    @Setter
    public static class Item {
        private Long productId;
        private Long variantId;
        private String itemName;
        private String productName;
        private String variantName;
        private Long imageFileId;
        private Integer quantity;
        private String unitLabel;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }

    @Getter
    @Setter
    public static class TimelineEvent {
        private String oldStatus;
        private String newStatus;
        private String reason;
        private LocalDateTime changedAt;
    }

    @Getter
    @Setter
    public static class Refund {
        private String refundCode;
        private String refundStatus;
        private BigDecimal requestedAmount;
        private BigDecimal approvedAmount;
        private String reason;
        private LocalDateTime initiatedAt;
        private LocalDateTime completedAt;
    }
}
