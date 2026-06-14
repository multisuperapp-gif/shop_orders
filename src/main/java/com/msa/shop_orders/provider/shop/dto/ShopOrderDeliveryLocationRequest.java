package com.msa.shop_orders.provider.shop.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ShopOrderDeliveryLocationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        // Encoded polyline of the route the agent currently has selected (optional);
        // streamed with the location so the customer app mirrors the driver's route.
        String routePolyline
) {
}
