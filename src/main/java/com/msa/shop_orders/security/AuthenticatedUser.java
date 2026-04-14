package com.msa.shop_orders.security;

import java.util.Set;

public record AuthenticatedUser(
        Long userId,
        Long sessionId,
        String publicUserId,
        Set<String> roles,
        String activeRole
) {
}
