package com.msa.shop_orders.consumer.order.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.integration.bookingpayment.ShopOrdersBookingPaymentOrderClient;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentApiResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentOrderDtos.CancelShopOrderRequest;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.service.ShopRuntimeViewService;
import com.msa.shop_orders.security.CurrentUserService;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerOrderLifecycleService {
    private final CurrentUserService currentUserService;
    private final ShopRuntimeViewService shopRuntimeViewService;
    private final ShopOrdersBookingPaymentOrderClient shopOrdersBookingPaymentOrderClient;

    public ConsumerOrderLifecycleService(
            CurrentUserService currentUserService,
            ShopRuntimeViewService shopRuntimeViewService,
            ShopOrdersBookingPaymentOrderClient shopOrdersBookingPaymentOrderClient
    ) {
        this.currentUserService = currentUserService;
        this.shopRuntimeViewService = shopRuntimeViewService;
        this.shopOrdersBookingPaymentOrderClient = shopOrdersBookingPaymentOrderClient;
    }

    @Transactional
    public void cancel(Long orderId, String reason) {
        Long userId = currentUserService.currentUser().userId();
        ShopOrderView order = shopRuntimeViewService.loadConsumerOrder(userId, orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        }
        try {
            ShopOrdersBookingPaymentApiResponse<Void> response = shopOrdersBookingPaymentOrderClient.cancelByUser(
                    userId,
                    new CancelShopOrderRequest(orderId, userId, reason)
            );
            if (response == null || !response.success()) {
                throw new BusinessException(
                        "ORDER_CANCEL_FAILED",
                        response == null || response.message() == null || response.message().isBlank()
                                ? "Order cancellation failed."
                                : response.message(),
                        HttpStatus.BAD_REQUEST
                );
            }
        } catch (FeignException.NotFound exception) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND);
        } catch (FeignException.BadRequest exception) {
            String content = exception.contentUTF8();
            throw new BusinessException(
                    "ORDER_CANCEL_FAILED",
                    content == null || content.isBlank() ? "Order cancellation failed." : content,
                    HttpStatus.BAD_REQUEST
            );
        } catch (FeignException exception) {
            throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "Order service is unavailable right now.", HttpStatus.BAD_REQUEST);
        }
    }
}
