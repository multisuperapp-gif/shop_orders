package com.msa.shop_orders.consumer.checkout.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.consumer.cart.view.ConsumerCartView;
import com.msa.shop_orders.consumer.cart.service.ConsumerCartService;
import com.msa.shop_orders.consumer.checkout.dto.ConsumerCheckoutPreviewData;
import com.msa.shop_orders.consumer.checkout.dto.ConsumerCheckoutPreviewRequest;
import com.msa.shop_orders.persistence.entity.ShopDeliveryRuleEntity;
import com.msa.shop_orders.persistence.entity.ShopLocationEntity;
import com.msa.shop_orders.persistence.entity.ShopOperatingHoursEntity;
import com.msa.shop_orders.persistence.entity.UserAddressEntity;
import com.msa.shop_orders.persistence.repository.ShopDeliveryRuleRepository;
import com.msa.shop_orders.persistence.repository.ShopLocationRepository;
import com.msa.shop_orders.persistence.repository.ShopOperatingHoursRepository;
import com.msa.shop_orders.persistence.repository.UserAddressRepository;
import com.msa.shop_orders.provider.shop.dto.ShopProductDeliveryRuleData;
import com.msa.shop_orders.provider.shop.service.ShopDeliveryRuleViewService;
import com.msa.shop_orders.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumerCheckoutService {
    private final CurrentUserService currentUserService;
    private final ConsumerCartService consumerCartService;
    private final ShopDeliveryRuleViewService shopDeliveryRuleViewService;
    private final UserAddressRepository userAddressRepository;
    private final ShopLocationRepository shopLocationRepository;
    private final ShopDeliveryRuleRepository shopDeliveryRuleRepository;
    private final ShopOperatingHoursRepository shopOperatingHoursRepository;

    public ConsumerCheckoutService(
            CurrentUserService currentUserService,
            ConsumerCartService consumerCartService,
            ShopDeliveryRuleViewService shopDeliveryRuleViewService,
            UserAddressRepository userAddressRepository,
            ShopLocationRepository shopLocationRepository,
            ShopDeliveryRuleRepository shopDeliveryRuleRepository,
            ShopOperatingHoursRepository shopOperatingHoursRepository
    ) {
        this.currentUserService = currentUserService;
        this.consumerCartService = consumerCartService;
        this.shopDeliveryRuleViewService = shopDeliveryRuleViewService;
        this.userAddressRepository = userAddressRepository;
        this.shopLocationRepository = shopLocationRepository;
        this.shopDeliveryRuleRepository = shopDeliveryRuleRepository;
        this.shopOperatingHoursRepository = shopOperatingHoursRepository;
    }

    @Transactional(readOnly = true)
    public ConsumerCheckoutPreviewData preview(ConsumerCheckoutPreviewRequest request) {
        Long userId = currentUserService.currentUser().userId();
        ConsumerCartView cart = consumerCartService.currentCartView();
        Long addressId = resolveDefaultAddressId(userId, request.addressId());
        AddressRow address = loadAddress(userId, addressId);
        ShopRuleRow shop = loadShopRule(cart.getShopId());
        int itemCount = cart.getItems().stream().mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
        BigDecimal subtotal = cart.getItems().stream()
                .map(item -> item.getLineTotal() == null ? BigDecimal.ZERO : item.getLineTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> issues = new ArrayList<>();
        if (!shop.openNow()) {
            issues.add("Shop is not open right now");
        }
        if (!shop.acceptsOrders()) {
            issues.add("Shop is not accepting orders right now");
        }
        if (subtotal.compareTo(shop.minOrderAmount()) < 0) {
            issues.add("Minimum order amount not reached");
        }

        String fulfillmentType = normalizeFulfillmentType(request.fulfillmentType());
        BigDecimal deliveryFee = "PICKUP".equals(fulfillmentType)
                ? BigDecimal.ZERO
                : calculateDeliveryFee(shop, subtotal);
        BigDecimal platformFee = BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.add(deliveryFee).add(platformFee).setScale(2, RoundingMode.HALF_UP);

        return new ConsumerCheckoutPreviewData(
                userId,
                cart.getShopId(),
                cart.getShopName(),
                address.addressId(),
                address.label(),
                address.addressLine(),
                fulfillmentType,
                itemCount,
                subtotal,
                deliveryFee,
                platformFee,
                totalAmount,
                cart.getCurrencyCode(),
                shop.openNow(),
                shop.closingSoon(),
                shop.acceptsOrders(),
                issues.isEmpty(),
                List.copyOf(issues)
        );
    }

    private Long resolveDefaultAddressId(Long userId, Long explicitAddressId) {
        if (explicitAddressId != null) {
            UserAddressEntity address = requireConsumerAddress(userId, explicitAddressId);
            if (address == null) {
                throw new BusinessException("ADDRESS_NOT_FOUND", "Address not found for this user.", HttpStatus.NOT_FOUND);
            }
            return address.getId();
        }
        UserAddressEntity address = userAddressRepository.findByUserIdAndAddressScopeOrderByDefaultAddressDescIdAsc(userId, "CONSUMER")
                .stream()
                .findFirst()
                .orElse(null);
        if (address == null) {
            throw new BusinessException("ADDRESS_NOT_FOUND", "Please add an address before checkout.", HttpStatus.NOT_FOUND);
        }
        return address.getId();
    }

    private AddressRow loadAddress(Long userId, Long addressId) {
        UserAddressEntity address = requireConsumerAddress(userId, addressId);
        if (address == null) {
            throw new BusinessException("ADDRESS_NOT_FOUND", "Address not found.", HttpStatus.NOT_FOUND);
        }
        return new AddressRow(address.getId(), address.getLabel(), formatAddress(address));
    }

    private ShopRuleRow loadShopRule(Long shopId) {
        ShopProductDeliveryRuleData rule = shopDeliveryRuleViewService.findPrimaryDeliveryRule(shopId)
                .orElseGet(() -> loadShopRuleSql(shopId).deliveryRule());
        OperatingHoursRow hours = loadOperatingHours(shopId);
        LocalTime now = LocalTime.now();
        boolean openNow = !hours.closed()
                && hours.openTime() != null
                && hours.closeTime() != null
                && !now.isBefore(hours.openTime())
                && !now.isAfter(hours.closeTime());
        boolean closingSoon = openNow && !now.isBefore(hours.closeTime().minusMinutes(rule.closingSoonMinutes() == null ? 60 : rule.closingSoonMinutes()));
        boolean acceptsOrders = !hours.closed()
                && hours.openTime() != null
                && hours.closeTime() != null
                && !now.isBefore(hours.openTime())
                && !now.isAfter(hours.closeTime().minusMinutes(rule.orderCutoffMinutesBeforeClose() == null ? 30 : rule.orderCutoffMinutesBeforeClose()));
        return new ShopRuleRow(
                shopId,
                hours.shopName(),
                defaultAmount(rule.deliveryFee()),
                defaultAmount(rule.freeDeliveryAbove()),
                defaultAmount(rule.minOrderAmount()),
                openNow,
                closingSoon,
                acceptsOrders,
                rule
        );
    }

    private SqlShopRuleRow loadShopRuleSql(Long shopId) {
        ShopLocationEntity primaryLocation = shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shopId).orElse(null);
        if (primaryLocation == null) {
            throw new BusinessException("SHOP_NOT_FOUND", "Shop not found.", HttpStatus.NOT_FOUND);
        }
        ShopDeliveryRuleEntity rule = shopDeliveryRuleRepository.findByShopLocationId(primaryLocation.getId()).orElse(null);
        return new SqlShopRuleRow(
                shopId,
                null,
                new ShopProductDeliveryRuleData(
                        primaryLocation.getId(),
                        rule == null ? "DELIVERY" : rule.getDeliveryType(),
                        rule == null ? BigDecimal.ZERO : rule.getRadiusKm(),
                        rule == null ? BigDecimal.ZERO : rule.getMinOrderAmount(),
                        rule == null ? BigDecimal.ZERO : rule.getDeliveryFee(),
                        rule == null ? new BigDecimal("999999999.99") : rule.getFreeDeliveryAbove(),
                        rule == null ? 30 : rule.getOrderCutoffMinutesBeforeClose(),
                        rule == null ? 60 : rule.getClosingSoonMinutes()
                )
        );
    }

    private OperatingHoursRow loadOperatingHours(Long shopId) {
        ShopLocationEntity primaryLocation = shopLocationRepository.findFirstByShopIdAndPrimaryTrueOrderByIdAsc(shopId).orElse(null);
        if (primaryLocation == null) {
            throw new BusinessException("SHOP_NOT_FOUND", "Shop not found.", HttpStatus.NOT_FOUND);
        }
        int weekday = java.time.LocalDate.now().getDayOfWeek().getValue() - 1;
        ShopOperatingHoursEntity hours = shopOperatingHoursRepository.findFirstByShopLocationIdAndWeekday(primaryLocation.getId(), weekday).orElse(null);
        return new OperatingHoursRow(
                null,
                hours != null && hours.isClosed(),
                hours == null ? null : hours.getOpenTime(),
                hours == null ? null : hours.getCloseTime()
        );
    }

    private static String normalizeFulfillmentType(String value) {
        if (value == null || value.isBlank()) {
            return "DELIVERY";
        }
        String normalized = value.trim().toUpperCase();
        if (!normalized.equals("DELIVERY") && !normalized.equals("PICKUP")) {
            throw new BusinessException("INVALID_FULFILLMENT_TYPE", "Unsupported fulfillmentType.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private static BigDecimal calculateDeliveryFee(ShopRuleRow shop, BigDecimal subtotal) {
        if (subtotal.compareTo(shop.freeDeliveryAbove()) >= 0) {
            return BigDecimal.ZERO;
        }
        return shop.deliveryFee().setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private UserAddressEntity requireConsumerAddress(Long userId, Long addressId) {
        return userAddressRepository.findById(addressId)
                .filter(address -> userId.equals(address.getUserId()))
                .filter(address -> "CONSUMER".equalsIgnoreCase(address.getAddressScope()))
                .orElse(null);
    }

    private String formatAddress(UserAddressEntity address) {
        StringBuilder builder = new StringBuilder();
        appendAddressPart(builder, address.getAddressLine1());
        appendAddressPart(builder, address.getAddressLine2());
        appendAddressPart(builder, address.getCity());
        appendAddressPart(builder, address.getPostalCode());
        return builder.toString();
    }

    private void appendAddressPart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(", ");
        }
        builder.append(value);
    }

    private record AddressRow(
            Long addressId,
            String label,
            String addressLine
    ) {
    }

    private record OperatingHoursRow(
            String shopName,
            boolean closed,
            LocalTime openTime,
            LocalTime closeTime
    ) {
    }

    private record SqlShopRuleRow(
            Long shopId,
            String shopName,
            ShopProductDeliveryRuleData deliveryRule
    ) {
    }

    private record ShopRuleRow(
            Long shopId,
            String shopName,
            BigDecimal deliveryFee,
            BigDecimal freeDeliveryAbove,
            BigDecimal minOrderAmount,
            boolean openNow,
            boolean closingSoon,
            boolean acceptsOrders,
            ShopProductDeliveryRuleData deliveryRule
    ) {
    }
}
