package com.msa.shop_orders.consumer.cart.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.consumer.cart.dto.ConsumerCartAddItemRequest;
import com.msa.shop_orders.consumer.cart.dto.ConsumerCartData;
import com.msa.shop_orders.consumer.cart.dto.ConsumerCartItemData;
import com.msa.shop_orders.consumer.cart.dto.ConsumerCartUpdateItemRequest;
import com.msa.shop_orders.consumer.cart.view.ConsumerCartView;
import com.msa.shop_orders.consumer.cart.view.repository.ConsumerCartViewRepository;
import com.msa.shop_orders.persistence.entity.FileEntity;
import com.msa.shop_orders.persistence.entity.ProductEntity;
import com.msa.shop_orders.persistence.entity.ProductImageEntity;
import com.msa.shop_orders.persistence.entity.ProductOptionEntity;
import com.msa.shop_orders.persistence.entity.ProductVariantEntity;
import com.msa.shop_orders.persistence.repository.FileRepository;
import com.msa.shop_orders.persistence.repository.ProductOptionRepository;
import com.msa.shop_orders.persistence.repository.ProductImageRepository;
import com.msa.shop_orders.persistence.repository.ProductRepository;
import com.msa.shop_orders.persistence.repository.ProductVariantRepository;
import com.msa.shop_orders.provider.shop.view.ShopShellView;
import com.msa.shop_orders.provider.shop.view.repository.ShopShellViewRepository;
import com.msa.shop_orders.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConsumerCartService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerCartService.class);
    private static final String DEFAULT_CURRENCY = "INR";

    private final ConsumerCartViewRepository consumerCartViewRepository;
    private final CurrentUserService currentUserService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductOptionRepository productOptionRepository;
    private final FileRepository fileRepository;
    private final ShopShellViewRepository shopShellViewRepository;

    public ConsumerCartService(
            ConsumerCartViewRepository consumerCartViewRepository,
            CurrentUserService currentUserService,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            ProductImageRepository productImageRepository,
            ProductOptionRepository productOptionRepository,
            FileRepository fileRepository,
            ShopShellViewRepository shopShellViewRepository
    ) {
        this.consumerCartViewRepository = consumerCartViewRepository;
        this.currentUserService = currentUserService;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.productImageRepository = productImageRepository;
        this.productOptionRepository = productOptionRepository;
        this.fileRepository = fileRepository;
        this.shopShellViewRepository = shopShellViewRepository;
    }

    @Transactional(readOnly = true)
    public ConsumerCartData currentCart() {
        Long userId = currentUserService.currentUser().userId();
        return toCartData(userId, consumerCartViewRepository.findById(userId).orElse(null));
    }

    @Transactional
    public ConsumerCartData addItem(ConsumerCartAddItemRequest request) {
        Long userId = currentUserService.currentUser().userId();
        ResolvedProduct product = resolveProduct(request.productId(), request.variantId());
        OptionSelection selection = resolveOptionSelection(request.productId(), request.optionIds());
        ConsumerCartView cart = consumerCartViewRepository.findById(userId).orElseGet(() -> newCart(userId, product));
        if (cart.getShopId() != null && !Objects.equals(cart.getShopId(), product.shopId())) {
            throw new BusinessException("CART_SHOP_MISMATCH", "Only one shop can stay active in the cart at a time.", HttpStatus.BAD_REQUEST);
        }
        ensureCartHeader(cart, product);

        String lineKey = buildLineKey(product.variantId(), selection.optionIds(), request.cookingRequest());
        ConsumerCartView.Item existingItem = cart.getItems().stream()
                .filter(item -> Objects.equals(item.getLineKey(), lineKey))
                .findFirst()
                .orElse(null);
        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + request.quantity());
            existingItem.setUnitPrice(product.sellingPrice().add(selection.priceDelta()));
            existingItem.setLineTotal(unitLineTotal(existingItem.getUnitPrice(), existingItem.getQuantity()));
            existingItem.setSelectedOptionIds(selection.optionIds());
            existingItem.setSelectedOptionNames(selection.optionNames());
            existingItem.setCookingRequest(normalizeText(request.cookingRequest()));
        } else {
            ConsumerCartView.Item item = new ConsumerCartView.Item();
            item.setItemId(nextItemId(cart));
            item.setLineKey(lineKey);
            item.setProductId(product.productId());
            item.setVariantId(product.variantId());
            item.setProductName(product.productName());
            item.setVariantName(product.variantName());
            item.setQuantity(request.quantity());
            item.setUnitPrice(product.sellingPrice().add(selection.priceDelta()));
            item.setLineTotal(unitLineTotal(item.getUnitPrice(), item.getQuantity()));
            item.setImageFileId(product.imageFileId());
            item.setSelectedOptionIds(selection.optionIds());
            item.setSelectedOptionNames(selection.optionNames());
            item.setCookingRequest(normalizeText(request.cookingRequest()));
            cart.getItems().add(item);
        }
        cart.setUpdatedAt(LocalDateTime.now());
        consumerCartViewRepository.save(cart);
        return toCartData(userId, cart);
    }

    @Transactional
    public ConsumerCartData updateItem(Long itemId, ConsumerCartUpdateItemRequest request) {
        Long userId = currentUserService.currentUser().userId();
        ConsumerCartView cart = requireCart(userId);
        ConsumerCartView.Item existing = cart.getItems().stream()
                .filter(item -> Objects.equals(item.getItemId(), itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("CART_ITEM_NOT_FOUND", "Cart item not found.", HttpStatus.NOT_FOUND));
        ResolvedProduct product = resolveProduct(existing.getProductId(), existing.getVariantId());
        OptionSelection selection = resolveOptionSelection(existing.getProductId(), request.optionIds());
        String lineKey = buildLineKey(existing.getVariantId(), selection.optionIds(), request.cookingRequest());
        ConsumerCartView.Item duplicate = cart.getItems().stream()
                .filter(item -> !Objects.equals(item.getItemId(), itemId))
                .filter(item -> Objects.equals(item.getLineKey(), lineKey))
                .findFirst()
                .orElse(null);
        if (duplicate != null) {
            duplicate.setQuantity(duplicate.getQuantity() + request.quantity());
            duplicate.setUnitPrice(product.sellingPrice().add(selection.priceDelta()));
            duplicate.setLineTotal(unitLineTotal(duplicate.getUnitPrice(), duplicate.getQuantity()));
            duplicate.setSelectedOptionIds(selection.optionIds());
            duplicate.setSelectedOptionNames(selection.optionNames());
            duplicate.setCookingRequest(normalizeText(request.cookingRequest()));
            cart.getItems().removeIf(item -> Objects.equals(item.getItemId(), itemId));
        } else {
            existing.setLineKey(lineKey);
            existing.setQuantity(request.quantity());
            existing.setUnitPrice(product.sellingPrice().add(selection.priceDelta()));
            existing.setLineTotal(unitLineTotal(existing.getUnitPrice(), existing.getQuantity()));
            existing.setSelectedOptionIds(selection.optionIds());
            existing.setSelectedOptionNames(selection.optionNames());
            existing.setCookingRequest(normalizeText(request.cookingRequest()));
        }
        cart.setUpdatedAt(LocalDateTime.now());
        consumerCartViewRepository.save(cart);
        return toCartData(userId, cart);
    }

    @Transactional
    public ConsumerCartData removeItem(Long itemId) {
        Long userId = currentUserService.currentUser().userId();
        ConsumerCartView cart = requireCart(userId);
        boolean removed = cart.getItems().removeIf(item -> Objects.equals(item.getItemId(), itemId));
        if (!removed) {
            throw new BusinessException("CART_ITEM_NOT_FOUND", "Cart item not found.", HttpStatus.NOT_FOUND);
        }
        if (cart.getItems().isEmpty()) {
            consumerCartViewRepository.deleteById(userId);
            return toCartData(userId, null);
        }
        cart.setUpdatedAt(LocalDateTime.now());
        consumerCartViewRepository.save(cart);
        return toCartData(userId, cart);
    }

    @Transactional(readOnly = true)
    public ConsumerCartView currentCartView() {
        Long userId = currentUserService.currentUser().userId();
        return requireCart(userId);
    }

    @Transactional
    public void clearCurrentCart() {
        Long userId = currentUserService.currentUser().userId();
        consumerCartViewRepository.deleteById(userId);
    }

    private ConsumerCartView requireCart(Long userId) {
        return consumerCartViewRepository.findById(userId)
                .filter(cart -> cart.getItems() != null && !cart.getItems().isEmpty())
                .orElseThrow(() -> new BusinessException("CART_EMPTY", "Active cart is empty.", HttpStatus.BAD_REQUEST));
    }

    private ConsumerCartView newCart(Long userId, ResolvedProduct product) {
        ConsumerCartView cart = new ConsumerCartView();
        cart.setUserId(userId);
        cart.setItems(new ArrayList<>());
        ensureCartHeader(cart, product);
        return cart;
    }

    private Long nextItemId(ConsumerCartView cart) {
        while (true) {
            long candidate = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 100000);
            boolean exists = cart.getItems().stream().anyMatch(item -> Objects.equals(item.getItemId(), candidate));
            if (!exists) {
                return candidate;
            }
        }
    }

    private void ensureCartHeader(ConsumerCartView cart, ResolvedProduct product) {
        cart.setShopId(product.shopId());
        cart.setShopName(product.shopName());
        cart.setCurrencyCode(DEFAULT_CURRENCY);
        cart.setCartContext("SHOP");
    }

    private ConsumerCartData toCartData(Long userId, ConsumerCartView cart) {
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return new ConsumerCartData(userId, null, null, DEFAULT_CURRENCY, null, 0, BigDecimal.ZERO, List.of());
        }
        Map<Long, String> imageKeysByFileId = fileRepository.findAllById(cart.getItems().stream()
                        .map(ConsumerCartView.Item::getImageFileId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()).stream()
                .collect(java.util.stream.Collectors.toMap(FileEntity::getId, FileEntity::getObjectKey));
        List<ConsumerCartItemData> items = cart.getItems().stream()
                .sorted(Comparator.comparing(ConsumerCartView.Item::getItemId))
                .map(item -> new ConsumerCartItemData(
                        item.getItemId(),
                        item.getLineKey(),
                        item.getProductId(),
                        item.getVariantId(),
                        item.getProductName(),
                        item.getVariantName(),
                        item.getQuantity(),
                        defaultAmount(item.getUnitPrice()),
                        defaultAmount(item.getLineTotal()),
                        item.getImageFileId() == null ? null : imageKeysByFileId.get(item.getImageFileId()),
                        item.getSelectedOptionNames() == null ? List.of() : List.copyOf(item.getSelectedOptionNames()),
                        item.getCookingRequest()
                ))
                .toList();
        BigDecimal subtotal = items.stream()
                .map(ConsumerCartItemData::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int itemCount = items.stream().mapToInt(ConsumerCartItemData::quantity).sum();
        return new ConsumerCartData(
                userId,
                cart.getShopId(),
                cart.getShopName(),
                defaultText(cart.getCurrencyCode(), DEFAULT_CURRENCY),
                cart.getCartContext(),
                itemCount,
                subtotal,
                items
        );
    }

    private ResolvedProduct resolveProduct(Long productId, Long requestedVariantId) {
        ProductEntity product = productRepository.findById(productId)
                .filter(ProductEntity::isActive)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product or variant not found.", HttpStatus.NOT_FOUND));
        ShopShellView shop = shopShellViewRepository.findById(product.getShopId())
                .filter(candidate -> "APPROVED".equalsIgnoreCase(candidate.getApprovalStatus()))
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Shop not found.", HttpStatus.NOT_FOUND));
        List<ProductVariantEntity> variants = productVariantRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                .filter(ProductVariantEntity::isActive)
                .toList();
        if (variants.isEmpty()) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "Product or variant not found.", HttpStatus.NOT_FOUND);
        }
        ProductVariantEntity variant = requestedVariantId == null
                ? variants.stream().filter(ProductVariantEntity::isDefaultVariant).findFirst().orElse(variants.getFirst())
                : variants.stream().filter(candidate -> Objects.equals(candidate.getId(), requestedVariantId)).findFirst()
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product or variant not found.", HttpStatus.NOT_FOUND));
        Long imageFileId = productImageRepository.findFirstByProductIdAndPrimaryImageTrue(productId)
                .map(ProductImageEntity::getFileId)
                .orElse(null);
        return new ResolvedProduct(
                product.getId(),
                product.getName(),
                shop.getShopId(),
                shop.getShopName(),
                variant.getId(),
                variant.getVariantName(),
                defaultAmount(variant.getSellingPrice()),
                imageFileId
        );
    }

    private OptionSelection resolveOptionSelection(Long productId, List<Long> optionIds) {
        List<Long> normalizedOptionIds = optionIds == null
                ? List.of()
                : optionIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (normalizedOptionIds.isEmpty()) {
            return new OptionSelection(List.of(), List.of(), BigDecimal.ZERO);
        }
        List<OptionRow> rows = productOptionRepository.findActiveOptionsForProduct(normalizedOptionIds, productId).stream()
                .map(option -> new OptionRow(
                        option.getId(),
                        option.getOptionName(),
                        defaultAmount(option.getPriceDelta())
                ))
                .toList();
        if (rows.size() != normalizedOptionIds.size()) {
            throw new BusinessException("INVALID_OPTIONS", "One or more selected options are invalid.", HttpStatus.BAD_REQUEST);
        }
        List<String> optionNames = rows.stream()
                .sorted(Comparator.comparing(OptionRow::id))
                .map(OptionRow::optionName)
                .toList();
        BigDecimal delta = rows.stream()
                .map(OptionRow::priceDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OptionSelection(normalizedOptionIds, optionNames, delta);
    }

    private String buildLineKey(Long variantId, List<Long> optionIds, String cookingRequest) {
        String raw = variantId + "|" + String.join(",", optionIds.stream().map(String::valueOf).toList()) + "|" + normalizeText(cookingRequest);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int index = 0; index < 16; index++) {
                hex.append(String.format("%02x", hash[index]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            LOGGER.error("SHA-256 is not available while building cart line key", exception);
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private BigDecimal unitLineTotal(BigDecimal unitPrice, Integer quantity) {
        return defaultAmount(unitPrice)
                .multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ResolvedProduct(
            Long productId,
            String productName,
            Long shopId,
            String shopName,
            Long variantId,
            String variantName,
            BigDecimal sellingPrice,
            Long imageFileId
    ) {
    }

    private record OptionRow(
            Long id,
            String optionName,
            BigDecimal priceDelta
    ) {
    }

    private record OptionSelection(
            List<Long> optionIds,
            List<String> optionNames,
            BigDecimal priceDelta
    ) {
    }
}
