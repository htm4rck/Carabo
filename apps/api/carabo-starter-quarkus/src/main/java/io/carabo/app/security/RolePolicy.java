package io.carabo.app.security;

import jakarta.ws.rs.HttpMethod;

final class RolePolicy {
    private RolePolicy() {}

    static boolean permits(String path, String method, HumanAccessService.Access access) {
        if (!access.allowed()) return false;
        if (path.startsWith("/tenants")) return access.hasAny("ADMIN_Carabo", "ROOT_PLATFORM");
        if ((path.matches("/documents/[^/]+/retry") && HttpMethod.POST.equals(method)) || path.startsWith("/documents/batch/")) {
            return access.hasAny("SUPERVISOR_Carabo", "ADMIN_Carabo", "ROOT_PLATFORM");
        }
        if (path.startsWith("/operations/") && HttpMethod.POST.equals(method)) {
            return access.hasAny("SUPERVISOR_Carabo", "ADMIN_Carabo", "ROOT_PLATFORM");
        }
        return access.hasAny("OPERATOR_Carabo", "SUPERVISOR_Carabo", "ADMIN_Carabo", "ROOT_PLATFORM");
    }
}
