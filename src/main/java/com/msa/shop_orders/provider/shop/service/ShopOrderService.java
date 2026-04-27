package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopOrderData;
import com.msa.shop_orders.provider.shop.dto.ShopOrderStatusUpdateRequest;

import java.time.LocalDate;
import java.util.List;

public interface ShopOrderService {
    List<ShopOrderData> orders(String dateFilter, String status, LocalDate fromDate, LocalDate toDate);
    ShopOrderData updateOrderStatus(Long orderId, ShopOrderStatusUpdateRequest request);
}
