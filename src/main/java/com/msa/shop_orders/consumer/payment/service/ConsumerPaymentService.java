package com.msa.shop_orders.consumer.payment.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.consumer.payment.dto.ConsumerPaymentDtos;
import com.msa.shop_orders.integration.bookingpayment.ShopOrdersBookingPaymentClient;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentApiResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentDtos;
import com.msa.shop_orders.persistence.entity.OrderEntity;
import com.msa.shop_orders.persistence.entity.PaymentEntity;
import com.msa.shop_orders.persistence.repository.OrderRepository;
import com.msa.shop_orders.persistence.repository.PaymentRepository;
import com.msa.shop_orders.security.CurrentUserService;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerPaymentService {
    private final CurrentUserService currentUserService;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ShopOrdersBookingPaymentClient shopOrdersBookingPaymentClient;

    public ConsumerPaymentService(
            CurrentUserService currentUserService,
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            ShopOrdersBookingPaymentClient shopOrdersBookingPaymentClient
    ) {
        this.currentUserService = currentUserService;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.shopOrdersBookingPaymentClient = shopOrdersBookingPaymentClient;
    }

    @Transactional(readOnly = true)
    public ConsumerPaymentDtos.PaymentStatusData status(String authorizationHeader, String paymentCode) {
        Long userId = currentUserService.currentUser().userId();
        requireShopOrderPaymentOwnership(userId, paymentCode);
        ShopOrdersBookingPaymentDtos.PaymentStatusResponse data = requireData(call(
                () -> shopOrdersBookingPaymentClient.status(authorizationHeader, userId, paymentCode)
        ));
        return mapStatus(data);
    }

    @Transactional(readOnly = true)
    public ConsumerPaymentDtos.PaymentInitiateData initiate(
            String authorizationHeader,
            String paymentCode,
            ConsumerPaymentDtos.PaymentInitiateRequest request
    ) {
        Long userId = currentUserService.currentUser().userId();
        requireShopOrderPaymentOwnership(userId, paymentCode);
        ShopOrdersBookingPaymentDtos.PaymentInitiateResponse data = requireData(call(
                () -> shopOrdersBookingPaymentClient.initiate(
                        authorizationHeader,
                        userId,
                        paymentCode,
                        new ShopOrdersBookingPaymentDtos.PaymentInitiateRequest(
                                request == null ? null : request.gatewayName()
                        )
                )
        ));
        return new ConsumerPaymentDtos.PaymentInitiateData(
                data.paymentId(),
                data.paymentCode(),
                data.gatewayName(),
                data.gatewayOrderId(),
                data.gatewayKeyId(),
                data.amount(),
                data.currencyCode(),
                data.paymentStatus(),
                data.payableType(),
                data.payableId()
        );
    }

    @Transactional(readOnly = true)
    public ConsumerPaymentDtos.PaymentStatusData verify(
            String authorizationHeader,
            String paymentCode,
            ConsumerPaymentDtos.PaymentVerifyRequest request
    ) {
        Long userId = currentUserService.currentUser().userId();
        requireShopOrderPaymentOwnership(userId, paymentCode);
        ShopOrdersBookingPaymentDtos.PaymentStatusResponse data = requireData(call(
                () -> shopOrdersBookingPaymentClient.verify(
                        authorizationHeader,
                        userId,
                        paymentCode,
                        new ShopOrdersBookingPaymentDtos.PaymentVerifyRequest(
                                request.gatewayOrderId(),
                                request.gatewayPaymentId(),
                                request.razorpaySignature()
                        )
                )
        ));
        return mapStatus(data);
    }

    @Transactional(readOnly = true)
    public ConsumerPaymentDtos.PaymentStatusData failure(
            String authorizationHeader,
            String paymentCode,
            ConsumerPaymentDtos.PaymentFailureRequest request
    ) {
        Long userId = currentUserService.currentUser().userId();
        requireShopOrderPaymentOwnership(userId, paymentCode);
        ShopOrdersBookingPaymentDtos.PaymentStatusResponse data = requireData(call(
                () -> shopOrdersBookingPaymentClient.failure(
                        authorizationHeader,
                        userId,
                        paymentCode,
                        new ShopOrdersBookingPaymentDtos.PaymentFailureRequest(
                                request == null ? null : request.gatewayOrderId(),
                                request == null ? null : request.failureCode(),
                                request == null ? null : request.failureMessage()
                        )
                )
        ));
        return mapStatus(data);
    }

    private void requireShopOrderPaymentOwnership(Long userId, String paymentCode) {
        PaymentEntity payment = paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.", HttpStatus.NOT_FOUND));
        if (!"SHOP_ORDER".equalsIgnoreCase(payment.getPayableType()) || !userId.equals(payment.getPayerUserId())) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.", HttpStatus.NOT_FOUND);
        }
        OrderEntity order = orderRepository.findByIdAndUserId(payment.getPayableId(), userId)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.", HttpStatus.NOT_FOUND));
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.", HttpStatus.NOT_FOUND);
        }
    }

    private ConsumerPaymentDtos.PaymentStatusData mapStatus(ShopOrdersBookingPaymentDtos.PaymentStatusResponse data) {
        return new ConsumerPaymentDtos.PaymentStatusData(
                data.paymentId(),
                data.paymentCode(),
                data.payableType(),
                data.payableId(),
                data.paymentStatus(),
                data.amount(),
                data.currencyCode(),
                data.gatewayName(),
                data.gatewayOrderId(),
                data.latestAttemptStatus(),
                data.latestGatewayTransactionId(),
                data.initiatedAt(),
                data.completedAt()
        );
    }

    private static <T> T requireData(ShopOrdersBookingPaymentApiResponse<T> response) {
        if (response == null) {
            throw new BusinessException("PAYMENT_RESPONSE_EMPTY", "Payment service returned an empty response.", HttpStatus.BAD_REQUEST);
        }
        if (!response.success()) {
            throw new BusinessException(
                    "PAYMENT_REQUEST_FAILED",
                    response.message() == null || response.message().isBlank() ? "Payment request failed." : response.message(),
                    HttpStatus.BAD_REQUEST
            );
        }
        if (response.data() == null) {
            throw new BusinessException("PAYMENT_RESPONSE_EMPTY", "Payment service returned no data.", HttpStatus.BAD_REQUEST);
        }
        return response.data();
    }

    private <T> ShopOrdersBookingPaymentApiResponse<T> call(FeignCall<T> call) {
        try {
            return call.execute();
        } catch (FeignException.NotFound exception) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.", HttpStatus.NOT_FOUND);
        } catch (FeignException.BadRequest exception) {
            throw new BusinessException(
                    "PAYMENT_REQUEST_FAILED",
                    extractMessage(exception),
                    HttpStatus.BAD_REQUEST
            );
        } catch (FeignException exception) {
            throw new BusinessException("PAYMENT_SERVICE_UNAVAILABLE", "Payment service is unavailable right now.", HttpStatus.BAD_REQUEST);
        }
    }

    private String extractMessage(FeignException exception) {
        String content = exception.contentUTF8();
        return content == null || content.isBlank() ? "Payment request failed." : content;
    }

    @FunctionalInterface
    private interface FeignCall<T> {
        ShopOrdersBookingPaymentApiResponse<T> execute();
    }
}
