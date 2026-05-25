package com.msa.shop_orders.provider.shop.view;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "shop_payments")
@Getter
@Setter
public class ShopPaymentView {
    @Id
    private Long paymentId;

    @Indexed(name = "uq_shop_payments_payment_code", unique = true)
    private String paymentCode;

    @Indexed
    private Long payableId;

    @Indexed
    private Long payerUserId;

    private String payableType;
    private String paymentStatus;
    private BigDecimal amount;
    private String currencyCode;
    private String gatewayName;
    private String gatewayOrderId;
    private String latestAttemptStatus;
    private String latestGatewayTransactionId;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
}
