package com.msa.shop_orders.provider.common;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.KycProfileEntity;
import com.msa.shop_orders.persistence.repository.KycProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProviderKycGate {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderKycGate.class);
    private final KycProfileRepository kycProfileRepository;

    public ProviderKycGate(KycProfileRepository kycProfileRepository) {
        this.kycProfileRepository = kycProfileRepository;
    }

    public void ensureAadhaarApproved(Long userId, String providerRole) {
        KycProfileEntity kycProfileEntity = kycProfileRepository.findByUser_IdAndKycType(userId, providerRole)
                .orElseThrow(() -> new BusinessException(
                        "AADHAAR_VERIFICATION_REQUIRED",
                        "Complete Aadhaar verification first.",
                        HttpStatus.FORBIDDEN
                ));
        if (!"APPROVED".equalsIgnoreCase(kycProfileEntity.getCurrentStatus())) {
            LOGGER.warn("KYC not approved for userId={}, providerRole={}, currentStatus={}", userId, providerRole, kycProfileEntity.getCurrentStatus());
            throw new BusinessException(
                    "AADHAAR_VERIFICATION_REQUIRED",
                    "Complete Aadhaar verification first.",
                    HttpStatus.FORBIDDEN
            );
        }
        LOGGER.debug("KYC approved for userId={}, providerRole={}", userId, providerRole);
    }
}
