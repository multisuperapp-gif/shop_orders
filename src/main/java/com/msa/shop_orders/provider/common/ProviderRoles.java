package com.msa.shop_orders.provider.common;

import java.util.Set;

public final class ProviderRoles {
    public static final String CUSTOMER = "CUSTOMER";
    public static final String LABOUR = "LABOUR";
    public static final String SERVICE_PROVIDER = "SERVICE_PROVIDER";
    public static final String SHOP_OWNER = "SHOP_OWNER";
    public static final Set<String> PROVIDER_SIDE_ROLES = Set.of(LABOUR, SERVICE_PROVIDER, SHOP_OWNER);

    private ProviderRoles() {
    }
}
