package com.msa.shop_orders.consumer.payment.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.consumer.payment.dto.ConsumerPaymentDtos;
import com.msa.shop_orders.integration.bookingpayment.ShopOrdersBookingPaymentClient;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentApiResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentDtos;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopPaymentView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopPaymentViewRepository;
import com.msa.shop_orders.security.CurrentUserService;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerPaymentService {
    private final CurrentUserService currentUserService;
    private final ShopPaymentViewRepository shopPaymentViewRepository;
    private final ShopOrderViewRepository shopOrderViewRepository;
    private final ShopOrdersBookingPaymentClient shopOrdersBookingPaymentClient;

    public ConsumerPaymentService(
            CurrentUserService currentUserService,
            ShopPaymentViewRepository shopPaymentViewRepository,
            ShopOrderViewRepository shopOrderViewRepository,
            ShopOrdersBookingPaymentClient shopOrdersBookingPaymentClient
    ) {
        this.currentUserService = currentUserService;
        this.shopPaymentViewRepository = shopPaymentViewRepository;
        this.shopOrderViewRepository = shopOrderViewRepository;
        this.shopOrdersBookingPaymentClient = shopOrdersBookingPaymentClient;
    }

    @Transactional(readOnly = true)
    public ConsumerPaymentDtos.PaymentStatusData status(String authorizationHeader, String paymentCode) {
        Long userId = currentUserService.currentUser().userId();
        requireShopOrderPaymentOwnership(userId, paymentCode);
        ShopOrdersBookingPaymentDtos.PaymentStatusResponse data = requireData(call(
                () -> shopOrdersBookingPaymentClient.status(authorizationHeader, userId, paymentCode)
        ));
        syncLocalPaymentStatus(data);
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
        // Accept-first: payment can only be made once the shop has accepted the
        // order (status ACCEPTED). Mirrors the booking accepted-then-pay flow.
        requireOrderAcceptedForPayment(paymentCode);
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
        syncLocalPaymentStatus(new ShopOrdersBookingPaymentDtos.PaymentStatusResponse(
                data.paymentId(),
                data.paymentCode(),
                data.payableType(),
                data.payableId(),
                data.paymentStatus(),
                data.amount(),
                data.currencyCode(),
                data.gatewayName(),
                data.gatewayOrderId(),
                data.paymentStatus(),
                null,
                null,
                null
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
        syncLocalPaymentStatus(data);
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
        syncLocalPaymentStatus(data);
        return mapStatus(data);
    }

    // Payment is allowed only when the order has been accepted by the shop and is
    // awaiting payment. Blocks payment on a still-pending or cancelled request.
    private void requireOrderAcceptedForPayment(String paymentCode) {
        ShopPaymentView payment = shopPaymentViewRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.", HttpStatus.NOT_FOUND));
        ShopOrderView order = shopOrderViewRepository.findById(payment.getPayableId())
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.", HttpStatus.NOT_FOUND));
        String status = order.getOrderStatus() == null ? "" : order.getOrderStatus().trim().toUpperCase();
        if ("PENDING_ACCEPTANCE".equals(status)) {
            throw new BusinessException(
                    "ORDER_NOT_ACCEPTED",
                    "The shop hasn't accepted your order yet. Payment opens once it's accepted.",
                    HttpStatus.CONFLICT
            );
        }
        // ACCEPTED (awaiting payment) and PAYMENT_PENDING are payable. Anything
        // else (CANCELLED / already paid / in progress) is rejected as a re-pay.
        if (!"ACCEPTED".equals(status) && !"PAYMENT_PENDING".equals(status)) {
            throw new BusinessException(
                    "ORDER_NOT_PAYABLE",
                    "This order can no longer be paid.",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void requireShopOrderPaymentOwnership(Long userId, String paymentCode) {
        ShopPaymentView payment = shopPaymentViewRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.", HttpStatus.NOT_FOUND));
        if (!"SHOP_ORDER".equalsIgnoreCase(payment.getPayableType()) || !userId.equals(payment.getPayerUserId())) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "Payment not found.", HttpStatus.NOT_FOUND);
        }
        ShopOrderView order = shopOrderViewRepository.findById(payment.getPayableId())
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

    private void syncLocalPaymentStatus(ShopOrdersBookingPaymentDtos.PaymentStatusResponse data) {
        if (data == null || data.paymentCode() == null || data.paymentCode().isBlank()) {
            return;
        }
        ShopPaymentView payment = shopPaymentViewRepository.findByPaymentCode(data.paymentCode())
                .orElseGet(ShopPaymentView::new);
        payment.setPaymentId(data.paymentId());
        payment.setPaymentCode(data.paymentCode());
        payment.setPayableType(data.payableType());
        payment.setPayableId(data.payableId());
        payment.setPaymentStatus(data.paymentStatus());
        payment.setAmount(data.amount());
        payment.setCurrencyCode(data.currencyCode());
        payment.setGatewayName(data.gatewayName());
        payment.setGatewayOrderId(data.gatewayOrderId());
        payment.setLatestAttemptStatus(data.latestAttemptStatus());
        payment.setLatestGatewayTransactionId(data.latestGatewayTransactionId());
        payment.setInitiatedAt(data.initiatedAt());
        payment.setCompletedAt(data.completedAt());
        shopPaymentViewRepository.save(payment);

        if (!"SHOP_ORDER".equalsIgnoreCase(data.payableType()) || data.payableId() == null) {
            return;
        }
        shopOrderViewRepository.findById(data.payableId()).ifPresent(order -> {
            order.setPaymentStatus(data.paymentStatus());
            if (data.paymentCode() != null && !data.paymentCode().isBlank()) {
                order.setPaymentCode(data.paymentCode());
            }
            shopOrderViewRepository.save(order);
        });
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
