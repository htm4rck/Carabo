package io.carabo.app.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.app.security.TenantContext;
import io.carabo.app.service.TenantCredentialsService;
import io.carabo.core.entity.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantCredentialsResourceTest {
  TenantCredentialsResource resource(Tenant found, TenantContext context) {
    var resource = new TenantCredentialsResource();
    resource.context = context;
    resource.credentials = new TenantCredentialsService();
    resource.em =
        (EntityManager)
            Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[] {EntityManager.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("find")) return found;
                  throw new UnsupportedOperationException();
                });
    return resource;
  }

  @Test
  void allCredentialOperationsDenyCrossCompanyAccessBeforeReadingOrWritingSecrets() {
    var own = new Tenant();
    own.setBusinessCode("B11111111111");
    var other = new Tenant();
    other.setBusinessCode("B22222222222");
    var context = new TenantContext();
    context.authenticateUser(own, "U1", Set.of("ADMIN_Carabo"), true);
    var resource = resource(other, context);
    var id = UUID.randomUUID();
    assertThrows(NotFoundException.class, () -> resource.status(id));
    assertThrows(NotFoundException.class, () -> resource.template(id));
    assertThrows(
        NotFoundException.class,
        () ->
            resource.save(
                id, new ObjectMapper().createObjectNode().put("greClientSecret", "test")));
    assertThrows(NotFoundException.class, () -> resource.importFile(id, null));
  }

  @Test
  void templateRemainsEmptyAndUncacheableEvenAfterSecretsAreConfigured() {
    var tenant = new Tenant();
    tenant.setBusinessCode("B11111111111");
    tenant.setGreClientId("hidden-id");
    tenant.setGreClientSecret("hidden-secret");
    var context = new TenantContext();
    context.authenticateAdministrator();
    var response = resource(tenant, context).template(UUID.randomUUID());
    String content = response.getEntity().toString();
    assertFalse(content.contains("hidden"));
    assertTrue(content.lines().allMatch(line -> line.endsWith("=")));
    assertEquals("no-store", response.getHeaderString("Cache-Control"));
  }
}
