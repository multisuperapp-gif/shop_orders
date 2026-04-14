package com.msa.shop_orders.provider.common;

import com.msa.shop_orders.common.exception.BusinessException;
import com.msa.shop_orders.persistence.entity.UserEntity;
import com.msa.shop_orders.persistence.entity.UserRoleEntity;
import com.msa.shop_orders.persistence.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class ProviderRoleBusinessValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderRoleBusinessValidator.class);
    private final UserRoleRepository userRoleRepository;

    public ProviderRoleBusinessValidator(UserRoleRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
    }

    public Optional<String> findProviderRole(Long userId) {
        List<UserRoleEntity> userRoles = userRoleRepository.findByUser_IdAndActiveTrue(userId);
        return userRoles.stream()
                .map(userRoleEntity -> userRoleEntity.getRole().getName())
                .filter(ProviderRoles.PROVIDER_SIDE_ROLES::contains)
                .findFirst();
    }

    public Set<String> activeRoles(Long userId) {
        return userRoleRepository.findByUser_IdAndActiveTrue(userId)
                .stream()
                .map(userRoleEntity -> userRoleEntity.getRole().getName())
                .collect(java.util.stream.Collectors.toSet());
    }

    public void ensureNoProviderRoleExists(UserEntity userEntity) {
        if (findProviderRole(userEntity.getId()).isPresent()) {
            LOGGER.warn("Provider role already exists for userId={}", userEntity.getId());
            throw new BusinessException(
                    "PROVIDER_ROLE_ALREADY_EXISTS",
                    "User already registered in provider app",
                    HttpStatus.CONFLICT
            );
        }
    }

    public void ensureRoleMatches(String expectedRole, Long userId) {
        String currentRole = findProviderRole(userId)
                .orElseThrow(() -> new BusinessException("PROVIDER_ROLE_NOT_FOUND", "Provider role not found", HttpStatus.BAD_REQUEST));
        if (!expectedRole.equals(currentRole)) {
            LOGGER.warn("Provider role mismatch for userId={}: expected={}, actual={}", userId, expectedRole, currentRole);
            throw new BusinessException("INVALID_PROVIDER_ROLE", "This API is not accessible for role " + currentRole, HttpStatus.BAD_REQUEST);
        }
        LOGGER.debug("Provider role validated for userId={}, role={}", userId, expectedRole);
    }
}
