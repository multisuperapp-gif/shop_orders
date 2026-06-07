package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHourData;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHourUpdateItem;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursData;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursUpdateRequest;
import com.msa.shop_orders.provider.shop.view.ShopOperatingHoursView;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShopOperatingHoursServiceImpl implements ShopOperatingHoursService {
    private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CLOSE_TIME = LocalTime.of(21, 0);

    private final ShopContextService shopContextService;
    private final ShopLocationViewService shopLocationViewService;
    private final ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    private final ShopOperatingHoursViewService shopOperatingHoursViewService;
    private final ShopShellViewService shopShellViewService;

    public ShopOperatingHoursServiceImpl(
            ShopContextService shopContextService,
            ShopLocationViewService shopLocationViewService,
            ShopDeliveryRuleViewService shopDeliveryRuleViewService,
            ShopOperatingHoursViewService shopOperatingHoursViewService,
            ShopShellViewService shopShellViewService
    ) {
        this.shopContextService = shopContextService;
        this.shopLocationViewService = shopLocationViewService;
        this.shopDeliveryRuleViewService = shopDeliveryRuleViewService;
        this.shopOperatingHoursViewService = shopOperatingHoursViewService;
        this.shopShellViewService = shopShellViewService;
    }

    @Override
    public ShopOperatingHoursData fetchCurrent() {
        ShopShellView shop = shopContextService.currentApprovedShop();
        Long locationId = resolvePrimaryLocationId(shop.getShopId());
        List<ShopOperatingHoursView> existingRows = shopOperatingHoursViewService.findByShopId(shop.getShopId());
        return buildData(shop.getShopId(), locationId, existingRows);
    }

    @Override
    @Transactional
    public ShopOperatingHoursData saveCurrent(ShopOperatingHoursUpdateRequest request) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        Long locationId = resolvePrimaryLocationId(shop.getShopId());
        List<ShopOperatingHoursView> existingRows = shopOperatingHoursViewService.findByShopId(shop.getShopId());
        Map<Integer, ShopOperatingHoursView> existingByWeekday = new LinkedHashMap<>();
        for (ShopOperatingHoursView row : existingRows) {
            existingByWeekday.put(row.getWeekday(), row);
        }

        Map<Integer, ShopOperatingHourUpdateItem> requestByWeekday = new LinkedHashMap<>();
        for (ShopOperatingHourUpdateItem item : request.days()) {
            if (item == null || item.weekday() == null) {
                continue;
            }
            if (requestByWeekday.put(item.weekday(), item) != null) {
                throw new BusinessException(
                        "DUPLICATE_WEEKDAY",
                        "Operating hours for a weekday can only be provided once.",
                        HttpStatus.BAD_REQUEST
                );
            }
        }

        List<ShopOperatingHoursView> rowsToSave = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            ShopOperatingHourUpdateItem item = requestByWeekday.get(weekday);
            ShopOperatingHoursView entity = existingByWeekday.getOrDefault(weekday, new ShopOperatingHoursView());
            entity.setId("shop:" + shop.getShopId() + ":weekday:" + weekday);
            entity.setShopId(shop.getShopId());
            entity.setShopLocationId(locationId);
            entity.setWeekday(weekday);

            if (item == null || item.closed()) {
                entity.setClosed(true);
                entity.setOpenTime(null);
                entity.setCloseTime(null);
            } else {
                LocalTime openTime = parseRequiredTime(item.openTime(), "Opening time is required.");
                LocalTime closeTime = parseRequiredTime(item.closeTime(), "Closing time is required.");
                if (!closeTime.isAfter(openTime)) {
                    throw new BusinessException(
                            "INVALID_OPERATING_HOURS",
                            "Closing time must be later than opening time.",
                            HttpStatus.BAD_REQUEST
                    );
                }
                entity.setClosed(false);
                entity.setOpenTime(formatTime(openTime));
                entity.setCloseTime(formatTime(closeTime));
            }
            rowsToSave.add(entity);
        }

        List<ShopOperatingHoursView> savedRows = shopOperatingHoursViewService.saveAll(rowsToSave)
                .stream()
                .sorted(Comparator.comparing(ShopOperatingHoursView::getWeekday))
                .toList();
        // Operating hours feed into the work-setup-complete flag — recompute now that
        // timing has changed (e.g. enabling a day can complete setup, disabling all days breaks it).
        shopShellViewService.checkAndUpdateBusinessSetupComplete(shop.getShopId());
        return buildData(shop.getShopId(), locationId, savedRows);
    }

    private Long resolvePrimaryLocationId(Long shopId) {
        return shopLocationViewService.findPrimaryLocationId(shopId)
                .orElseThrow(() -> new BusinessException(
                        "SHOP_LOCATION_NOT_FOUND",
                        "Primary shop location is not available yet.",
                        HttpStatus.BAD_REQUEST
                ));
    }

    private ShopOperatingHoursData buildData(
            Long shopId,
            Long locationId,
            List<ShopOperatingHoursView> existingRows
    ) {
        Map<Integer, ShopOperatingHoursView> existingByWeekday = new LinkedHashMap<>();
        for (ShopOperatingHoursView row : existingRows) {
            existingByWeekday.put(row.getWeekday(), row);
        }

        List<ShopOperatingHourData> days = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            ShopOperatingHoursView row = existingByWeekday.get(weekday);
            days.add(new ShopOperatingHourData(
                    weekday,
                    row == null || row.isClosed(),
                    row == null || row.getOpenTime() == null ? formatTime(DEFAULT_OPEN_TIME) : row.getOpenTime(),
                    row == null || row.getCloseTime() == null ? formatTime(DEFAULT_CLOSE_TIME) : row.getCloseTime()
            ));
        }

        var deliveryRule = shopDeliveryRuleViewService.findPrimaryDeliveryRule(shopId).orElse(null);
        return new ShopOperatingHoursData(
                locationId,
                deliveryRule != null && deliveryRule.orderCutoffMinutesBeforeClose() != null
                        ? deliveryRule.orderCutoffMinutesBeforeClose()
                        : 30,
                deliveryRule != null && deliveryRule.closingSoonMinutes() != null
                        ? deliveryRule.closingSoonMinutes()
                        : 60,
                days
        );
    }

    private LocalTime parseRequiredTime(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("INVALID_OPERATING_HOURS", message, HttpStatus.BAD_REQUEST);
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    "INVALID_OPERATING_HOURS",
                    "Time must use HH:mm format.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private String formatTime(LocalTime value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }
}
