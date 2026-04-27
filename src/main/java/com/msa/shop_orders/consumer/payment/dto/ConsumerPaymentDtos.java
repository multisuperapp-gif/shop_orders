package com.msa.shop_orders.consumer.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ConsumerPaymentDtos {
    private ConsumerPaymentDtos() {
    }

    public record PaymentInitiateRequest(
            String gatewayName
    ) {
    }

    public record PaymentVerifyRequest(
            String gatewayOrderId,
            String gatewayPaymentId,
            String razorpaySignature
    ) {
    }

    public record PaymentFailureRequest(
            String gatewayOrderId,
            String failureCode,
            String failureMessage
    ) {
    }

    public record PaymentStatusData(
            Long paymentId,
            String paymentCode,
            String payableType,
            Long payableId,
            String paymentStatus,
            BigDecimal amount,
            String currencyCode,
            String gatewayName,
            String gatewayOrderId,
            String latestAttemptStatus,
            String latestGatewayTransactionId,
            LocalDateTime initiatedAt,
            LocalDateTime completedAt
    ) {
    }

    public record PaymentInitiateData(
            Long paymentId,
            String paymentCode,
            String gatewayName,
            String gatewayOrderId,
            String gatewayKeyId,
            BigDecimal amount,
            String currencyCode,
            String paymentStatus,
            String payableType,
            Long payableId
    ) {
    }
}
