package io.carabo.app.security;

import static org.junit.jupiter.api.Assertions.*;

import io.carabo.core.entity.Tenant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CredentialCipherTest {
  @TempDir Path secrets;

  CredentialCipher cipher() throws Exception {
    Files.writeString(
        secrets.resolve("key"), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    var cipher = new CredentialCipher();
    cipher.basePath = secrets.toString();
    cipher.keyReference = "secret://key";
    return cipher;
  }

  @Test
  void encryptionIsRandomAndAuthenticatedByCompanyAndField() throws Exception {
    var cipher = cipher();
    var company = UUID.randomUUID();
    String secret = "test-credential==";
    String first = cipher.encrypt(company, "greClientSecret", secret),
        second = cipher.encrypt(company, "greClientSecret", secret);
    assertNotEquals(first, second);
    assertFalse(first.contains(secret));
    assertEquals(secret, cipher.decrypt(company, "greClientSecret", first));
    assertThrows(
        IllegalStateException.class,
        () -> cipher.decrypt(UUID.randomUUID(), "greClientSecret", first));
    assertThrows(IllegalStateException.class, () -> cipher.decrypt(company, "solPassword", first));
    byte[] bytes = java.util.Base64.getDecoder().decode(first.substring(3));
    bytes[15] ^= 1;
    assertThrows(
        IllegalStateException.class,
        () ->
            cipher.decrypt(
                company,
                "greClientSecret",
                "v1:" + java.util.Base64.getEncoder().encodeToString(bytes)));
  }

  @Test
  void missingWrongOrEscapingKeyFailsClosedWithoutReturningInput() throws Exception {
    var cipher = cipher();
    var company = UUID.randomUUID();
    String value = cipher.encrypt(company, "solPassword", "test-only-value");
    Files.writeString(
        secrets.resolve("key"), "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    assertThrows(IllegalStateException.class, () -> cipher.decrypt(company, "solPassword", value));
    cipher.keyReference = "secret://../escape";
    assertFalse(cipher.available());
    cipher.keyReference = "secret://missing";
    assertFalse(cipher.available());
    assertFalse(
        assertThrows(
                IllegalStateException.class,
                () -> cipher.encrypt(company, "solPassword", "test-only-value"))
            .getMessage()
            .contains("test-only-value"));
  }

  @Test
  void companyAdministratorCannotManageAnotherCompanyAndProducerCannotManageCredentials() {
    var first = new Tenant();
    first.setBusinessCode("B11111111111");
    var second = new Tenant();
    second.setBusinessCode("B22222222222");
    var context = new TenantContext();
    context.authenticateUser(first, "U1", Set.of("ADMIN_Carabo"), true);
    assertTrue(context.canManage(first));
    assertFalse(context.canManage(second));
    context.authenticateUser(first, "U1", Set.of("ROOT_PLATFORM"), true);
    assertTrue(context.canManage(second));
    context.authenticateTenant(first);
    assertFalse(context.canManage(first));
    context = new TenantContext();
    context.authenticateAdministrator();
    assertTrue(context.canManage(second));
  }
}
