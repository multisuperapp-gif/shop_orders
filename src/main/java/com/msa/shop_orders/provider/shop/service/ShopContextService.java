package com.msa.shop_orders.provider.shop.service;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.ShopEntity;
import com.msa.shop_orders.persistence.repository.ShopRepository;
import com.msa.shop_orders.provider.common.ProviderKycGate;
import com.msa.shop_orders.provider.common.ProviderRoleBusinessValidator;
import com.msa.shop_orders.provider.common.ProviderRoles;
import com.msa.shop_orders.security.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ShopContextService {
    private final CurrentUserService currentUserService;
    private final ProviderRoleBusinessValidator providerRoleBusinessValidator;
    private final ProviderKycGate providerKycGate;
    private final ShopRepository shopRepository;

    public ShopContextService(
            CurrentUserService currentUserService,
            ProviderRoleBusinessValidator providerRoleBusinessValidator,
            ProviderKycGate providerKycGate,
            ShopRepository shopRepository
    ) {
        this.currentUserService = currentUserService;
        this.providerRoleBusinessValidator = providerRoleBusinessValidator;
        this.providerKycGate = providerKycGate;
        this.shopRepository = shopRepository;
    }

    public ShopEntity currentApprovedShop() {
        Long userId = currentUserService.currentUser().userId();
        providerRoleBusinessValidator.ensureRoleMatches(ProviderRoles.SHOP_OWNER, userId);
        providerKycGate.ensureAadhaarApproved(userId, ProviderRoles.SHOP_OWNER);
        ShopEntity shopEntity = shopRepository.findFirstByOwnerUserIdOrderByIdDesc(userId)
                .orElseThrow(() -> new BusinessException("SHOP_NOT_FOUND", "Shop profile not found", HttpStatus.NOT_FOUND));
        if (!"APPROVED".equalsIgnoreCase(shopEntity.getApprovalStatus())) {
            throw new BusinessException("SHOP_APPROVAL_REQUIRED", "Shop must be approved before accessing dashboard features.", HttpStatus.BAD_REQUEST);
        }
        return shopEntity;
    }
}
