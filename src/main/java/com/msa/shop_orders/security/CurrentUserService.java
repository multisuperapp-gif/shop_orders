package com.msa.shop_orders.security;

import com.msa.shop_orders.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentUserService.class);

    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            LOGGER.warn("Authenticated user not found in security context");
            throw new UnauthorizedException("Authenticated user not found");
        }
        return authenticatedUser;
    }
}
