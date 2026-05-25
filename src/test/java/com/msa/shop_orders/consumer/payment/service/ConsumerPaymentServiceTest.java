package com.msa.shop_orders.consumer.payment.service;

import com.msa.shop_orders.consumer.payment.dto.ConsumerPaymentDtos;
import com.msa.shop_orders.integration.bookingpayment.ShopOrdersBookingPaymentClient;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentApiResponse;
import com.msa.shop_orders.integration.bookingpayment.dto.ShopOrdersBookingPaymentDtos;
import com.msa.shop_orders.provider.shop.view.ShopOrderView;
import com.msa.shop_orders.provider.shop.view.ShopPaymentView;
import com.msa.shop_orders.provider.shop.view.repository.ShopOrderViewRepository;
import com.msa.shop_orders.provider.shop.view.repository.ShopPaymentViewRepository;
import com.msa.shop_orders.security.AuthenticatedUser;
import com.msa.shop_orders.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerPaymentServiceTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ShopPaymentViewRepository shopPaymentViewRepository;
    @Mock
    private ShopOrderViewRepository shopOrderViewRepository;
    @Mock
    private ShopOrdersBookingPaymentClient shopOrdersBookingPaymentClient;

    private ConsumerPaymentService service;

    @BeforeEach
    void setUp() {
        service = new ConsumerPaymentService(
                currentUserService,
                shopPaymentViewRepository,
                shopOrderViewRepository,
                shopOrdersBookingPaymentClient
        );
    }

    @Test
    void statusSyncsMongoPaymentMirrorAndOrderPaymentStatus() {
        AuthenticatedUser user = new AuthenticatedUser(7L, 1L, "USR-7", Set.of("CONSUMER"), "CONSUMER");
        ShopPaymentView payment = new ShopPaymentView();
        payment.setPaymentId(801L);
        payment.setPaymentCode("PAY-1");
        payment.setPayableType("SHOP_ORDER");
        payment.setPayableId(501L);
        payment.setPayerUserId(7L);
        payment.setPaymentStatus("CREATED");

        ShopOrderView order = new ShopOrderView();
        order.setOrderId(501L);
        order.setUserId(7L);
        order.setPaymentStatus("UNPAID");

        ShopOrdersBookingPaymentDtos.PaymentStatusResponse remote = new ShopOrdersBookingPaymentDtos.PaymentStatusResponse(
                801L,
                "PAY-1",
                "SHOP_ORDER",
                501L,
                "SUCCESS",
                new BigDecimal("265"),
                "INR",
                "RAZORPAY",
                "GO-1",
                "CAPTURED",
                "GTX-1",
                LocalDateTime.of(2026, 5, 25, 10, 0),
                LocalDateTime.of(2026, 5, 25, 10, 5)
        );

        when(currentUserService.currentUser()).thenReturn(user);
        when(shopPaymentViewRepository.findByPaymentCode("PAY-1")).thenReturn(Optional.of(payment));
        when(shopOrderViewRepository.findById(501L)).thenReturn(Optional.of(order));
        when(shopOrdersBookingPaymentClient.status("Bearer token", 7L, "PAY-1"))
                .thenReturn(new ShopOrdersBookingPaymentApiResponse<>(true, "ok", null, remote));

        ConsumerPaymentDtos.PaymentStatusData data = service.status("Bearer token", "PAY-1");

        assertEquals("SUCCESS", data.paymentStatus());
        assertEquals("CAPTURED", data.latestAttemptStatus());

        ArgumentCaptor<ShopPaymentView> paymentCaptor = ArgumentCaptor.forClass(ShopPaymentView.class);
        verify(shopPaymentViewRepository).save(paymentCaptor.capture());
        assertEquals("SUCCESS", paymentCaptor.getValue().getPaymentStatus());
        assertEquals("GO-1", paymentCaptor.getValue().getGatewayOrderId());

        ArgumentCaptor<ShopOrderView> orderCaptor = ArgumentCaptor.forClass(ShopOrderView.class);
        verify(shopOrderViewRepository).save(orderCaptor.capture());
        assertEquals("SUCCESS", orderCaptor.getValue().getPaymentStatus());
        assertEquals("PAY-1", orderCaptor.getValue().getPaymentCode());
    }
}
