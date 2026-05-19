package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursData;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursUpdateRequest;

public interface ShopOperatingHoursService {
    ShopOperatingHoursData fetchCurrent();
    ShopOperatingHoursData saveCurrent(ShopOperatingHoursUpdateRequest request);
}
