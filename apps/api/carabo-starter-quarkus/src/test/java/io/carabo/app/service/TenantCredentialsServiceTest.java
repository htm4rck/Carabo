package io.carabo.app.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TenantCredentialsServiceTest {
  @Test
  void templateIsAlwaysEmptyAndBase64EqualsAndPasswordSpacesSurviveImport() {
    String template = TenantCredentialsService.blankTemplate();
    assertEquals(6, template.lines().count());
    assertTrue(template.lines().allMatch(line -> line.matches("[A-Z_]+=")));
    var values =
        TenantCredentialsService.parse(
            "\uFEFF# comment\r\n"
                + "SOL_USER=  20123456789MODDATOS  \r\n"
                + "GRE_CLIENT_SECRET=test-credential==\r\n"
                + "SOL_PASSWORD= space password \r\n"
                + "OSE_API_TOKEN=\r\n");
    assertEquals("test-credential==", values.get("greClientSecret"));
    assertEquals(" space password ", values.get("solPassword"));
    assertEquals("20123456789MODDATOS", values.get("solUser"));
    assertFalse(values.containsKey("oseApiToken"));
  }

  @Test
  void invalidOrEmptyFilesNeverEchoCredentialValues() {
    for (String content :
        new String[] {
          TenantCredentialsService.blankTemplate(),
          "UNKNOWN=test-secret",
          "SOL_PASSWORD=test-secret\nSOL_PASSWORD=second",
          "test-secret",
          "SOL_PASSWORD=secret://someone-else",
          "SOL_PASSWORD=test\u0000secret",
          "SOL_PASSWORD=" + "s".repeat(17000)
        }) {
      var error =
          assertThrows(
              IllegalArgumentException.class, () -> TenantCredentialsService.parse(content));
      assertFalse(error.getMessage().contains("test-secret"));
    }
  }

  @Test
  void blankFieldsPreserveStoredValuesAndUnknownJsonFieldsAreRejected() {
    assertEquals(
        Map.of("greClientId", "client-id"),
        TenantCredentialsService.validate(Map.of("solPassword", "", "greClientId", " client-id ")));
    assertThrows(
        IllegalArgumentException.class,
        () -> TenantCredentialsService.validate(Map.of("tenantId", "other-company")));
  }
}
