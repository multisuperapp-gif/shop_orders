package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.ShopOperatingHoursEntity;
import com.msa.shop_orders.persistence.repository.ShopOperatingHoursRepository;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHourData;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHourUpdateItem;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursData;
import com.msa.shop_orders.provider.shop.dto.ShopOperatingHoursUpdateRequest;
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
    private final ShopOperatingHoursRepository shopOperatingHoursRepository;

    public ShopOperatingHoursServiceImpl(
            ShopContextService shopContextService,
            ShopLocationViewService shopLocationViewService,
            ShopDeliveryRuleViewService shopDeliveryRuleViewService,
            ShopOperatingHoursRepository shopOperatingHoursRepository
    ) {
        this.shopContextService = shopContextService;
        this.shopLocationViewService = shopLocationViewService;
        this.shopDeliveryRuleViewService = shopDeliveryRuleViewService;
        this.shopOperatingHoursRepository = shopOperatingHoursRepository;
    }

    @Override
    public ShopOperatingHoursData fetchCurrent() {
        ShopShellView shop = shopContextService.currentApprovedShop();
        Long locationId = resolvePrimaryLocationId(shop.getShopId());
        List<ShopOperatingHoursEntity> existingRows = shopOperatingHoursRepository
                .findByShopLocationIdOrderByWeekdayAsc(locationId);
        return buildData(shop.getShopId(), locationId, existingRows);
    }

    @Override
    @Transactional
    public ShopOperatingHoursData saveCurrent(ShopOperatingHoursUpdateRequest request) {
        ShopShellView shop = shopContextService.currentApprovedShop();
        Long locationId = resolvePrimaryLocationId(shop.getShopId());
        List<ShopOperatingHoursEntity> existingRows = shopOperatingHoursRepository
                .findByShopLocationIdOrderByWeekdayAsc(locationId);
        Map<Integer, ShopOperatingHoursEntity> existingByWeekday = new LinkedHashMap<>();
        for (ShopOperatingHoursEntity row : existingRows) {
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

        List<ShopOperatingHoursEntity> rowsToSave = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            ShopOperatingHourUpdateItem item = requestByWeekday.get(weekday);
            ShopOperatingHoursEntity entity = existingByWeekday.getOrDefault(weekday, new ShopOperatingHoursEntity());
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
                entity.setOpenTime(openTime);
                entity.setCloseTime(closeTime);
            }
            rowsToSave.add(entity);
        }

        List<ShopOperatingHoursEntity> savedRows = shopOperatingHoursRepository.saveAll(rowsToSave)
                .stream()
                .sorted(Comparator.comparing(ShopOperatingHoursEntity::getWeekday))
                .toList();
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
            List<ShopOperatingHoursEntity> existingRows
    ) {
        Map<Integer, ShopOperatingHoursEntity> existingByWeekday = new LinkedHashMap<>();
        for (ShopOperatingHoursEntity row : existingRows) {
            existingByWeekday.put(row.getWeekday(), row);
        }

        List<ShopOperatingHourData> days = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            ShopOperatingHoursEntity row = existingByWeekday.get(weekday);
            days.add(new ShopOperatingHourData(
                    weekday,
                    row == null || row.isClosed(),
                    formatTime(row != null ? row.getOpenTime() : DEFAULT_OPEN_TIME),
                    formatTime(row != null ? row.getCloseTime() : DEFAULT_CLOSE_TIME)
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
