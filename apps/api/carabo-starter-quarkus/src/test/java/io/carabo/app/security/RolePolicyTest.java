package io.carabo.app.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RolePolicyTest {
    @Test
    void operatorCanReadAndEmitButCannotAdministerOrRetry() {
        var access = new HumanAccessService.Access(true, false, Set.of("OPERATOR_Carabo"));
        assertTrue(RolePolicy.permits("/documents", "GET", access));
        assertTrue(RolePolicy.permits("/documents/emit", "POST", access));
        assertFalse(RolePolicy.permits("/tenants", "GET", access));
        assertFalse(RolePolicy.permits("/documents/abc/retry", "POST", access));
    }

    @Test
    void supervisorCanRetryAndRunBatchButCannotAdministerTenants() {
        var access = new HumanAccessService.Access(true, false, Set.of("SUPERVISOR_Carabo"));
        assertTrue(RolePolicy.permits("/documents/abc/retry", "POST", access));
        assertTrue(RolePolicy.permits("/documents/batch/summary", "POST", access));
        assertFalse(RolePolicy.permits("/tenants", "GET", access));
        assertTrue(RolePolicy.permits("/fiscal-status", "GET", access));
        assertTrue(RolePolicy.permits("/integrations", "GET", access));
        assertFalse(RolePolicy.permits("/integrations", "POST", access));
    }

    @Test
    void adminAndRootCanAdministerTenants() {
        assertTrue(RolePolicy.permits("/tenants", "GET", new HumanAccessService.Access(true, false, Set.of("ADMIN_Carabo"))));
        assertTrue(RolePolicy.permits("/tenants", "POST", new HumanAccessService.Access(true, true, Set.of("ROOT_PLATFORM"))));
        assertTrue(RolePolicy.permits("/integrations", "POST", new HumanAccessService.Access(true, false, Set.of("ADMIN_Carabo"))));
    }
}
