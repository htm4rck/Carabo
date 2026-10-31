package io.carabo.app.service;

import io.carabo.app.security.ManagedCredentialStore;
import io.carabo.app.security.SecretResolver;
import io.carabo.core.entity.Tenant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class TenantCredentialsService {
  public static final Map<String, String> TEMPLATE_FIELDS;

  static {
    var fields = new LinkedHashMap<String, String>();
    fields.put("SOL_USER", "solUser");
    fields.put("SOL_PASSWORD", "solPassword");
    fields.put("GRE_CLIENT_ID", "greClientId");
    fields.put("GRE_CLIENT_SECRET", "greClientSecret");
    fields.put("CERTIFICATE_PASSWORD", "certificatePassword");
    fields.put("OSE_API_TOKEN", "oseApiToken");
    TEMPLATE_FIELDS = java.util.Collections.unmodifiableMap(fields);
  }

  @Inject ManagedCredentialStore store;
  @Inject SecretResolver resolver;
  @Inject CertificateService certificates;
  @Inject TenantService tenants;

  public static String blankTemplate() {
    return String.join("\n", TEMPLATE_FIELDS.keySet().stream().map(key -> key + "=").toList())
        + "\n";
  }

  public static Map<String, String> parse(String content) {
    if (content == null || content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16384)
      throw new IllegalArgumentException("El archivo de credenciales supera el límite de 16 KB");
    if (content.startsWith("\uFEFF")) content = content.substring(1);
    var values = new LinkedHashMap<String, String>();
    var seen = new java.util.HashSet<String>();
    for (String line : content.split("\\r?\\n", -1)) {
      if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
      int separator = line.indexOf('=');
      if (separator < 1)
        throw new IllegalArgumentException("Usa una línea CAMPO=valor por credencial");
      String name = TEMPLATE_FIELDS.get(line.substring(0, separator).trim());
      if (name == null)
        throw new IllegalArgumentException(
            "El archivo contiene un campo desconocido; descarga la plantilla vacía");
      if (!seen.add(name))
        throw new IllegalArgumentException("El archivo contiene campos repetidos");
      values.put(name, line.substring(separator + 1));
    }
    return validate(values);
  }

  public static Map<String, String> validate(Map<String, String> input) {
    if (input == null || input.size() > 6)
      throw new IllegalArgumentException("Formato de credenciales inválido");
    var clean = new LinkedHashMap<String, String>();
    for (var entry : input.entrySet()) {
      if (!TEMPLATE_FIELDS.containsValue(entry.getKey()))
        throw new IllegalArgumentException("Hay un campo de credencial desconocido");
      String value = entry.getValue();
      if (value == null || value.isBlank()) continue;
      int maximum =
          entry.getKey().equals("solUser")
              ? 100
              : entry.getKey().equals("greClientId") ? 200 : 4096;
      if (value.length() > maximum || value.chars().anyMatch(c -> c < 32 || c == 127))
        throw new IllegalArgumentException("Revisa el formato y longitud de las credenciales");
      if (value.startsWith("secret://"))
        throw new IllegalArgumentException(
            "Ingresa el valor de la credencial; las referencias se administran automáticamente");
      clean.put(
          entry.getKey(),
          Set.of("solUser", "greClientId").contains(entry.getKey()) ? value.trim() : value);
    }
    if (clean.isEmpty())
      throw new IllegalArgumentException(
          "Completa al menos una credencial; los campos vacíos conservan sus valores actuales");
    return clean;
  }

  public CredentialStatus update(Tenant tenant, Map<String, String> input, String actor) {
    var values = validate(input);
    // Validate the entire change before storing any credential version.
    if (values.containsKey("certificatePassword") && tenant.getCertificateFile() != null)
      certificates.verifyPassword(tenant.getCertificateFile(), values.get("certificatePassword"));
    var prepared = new LinkedHashMap<String, ManagedCredentialStore.Prepared>();
    for (var value : values.entrySet())
      if (!Set.of("solUser", "greClientId").contains(value.getKey()))
        prepared.put(
            value.getKey(), store.prepare(tenant.getId(), value.getKey(), value.getValue()));
    for (var value : values.entrySet()) {
      String stored =
          prepared.containsKey(value.getKey())
              ? store.persist(prepared.get(value.getKey()), actor)
              : value.getValue();
      switch (value.getKey()) {
        case "solUser" -> tenant.setSolUser(stored);
        case "solPassword" -> tenant.setSolPassword(stored);
        case "greClientId" -> tenant.setGreClientId(stored);
        case "greClientSecret" -> tenant.setGreClientSecret(stored);
        case "certificatePassword" -> tenant.setCertificatePassword(stored);
        case "oseApiToken" -> tenant.setOseApiToken(stored);
        default -> throw new IllegalArgumentException("Campo de credencial inválido");
      }
    }
    tenants.evict(tenant.getBusinessCode());
    return status(tenant);
  }

  public CredentialStatus status(Tenant tenant) {
    var fields = new LinkedHashMap<String, FieldStatus>();
    field(fields, tenant, "solUser", tenant.getSolUser(), false);
    field(fields, tenant, "solPassword", tenant.getSolPassword(), true);
    field(fields, tenant, "greClientId", tenant.getGreClientId(), false);
    field(fields, tenant, "greClientSecret", tenant.getGreClientSecret(), true);
    field(fields, tenant, "certificatePassword", tenant.getCertificatePassword(), true);
    field(fields, tenant, "oseApiToken", tenant.getOseApiToken(), true);
    return new CredentialStatus(store.available(), fields);
  }

  private void field(
      Map<String, FieldStatus> result, Tenant tenant, String name, String value, boolean secret) {
    boolean configured = value != null && !value.isBlank(), usable = configured;
    if (secret && configured) {
      try {
        usable =
            (!value.startsWith(ManagedCredentialStore.PREFIX)
                    || store.belongsTo(value, tenant.getId(), name))
                && !resolver.resolve(value).isBlank();
      } catch (RuntimeException error) {
        usable = false;
      }
    }
    result.put(name, new FieldStatus(configured, usable));
  }

  public record FieldStatus(boolean configured, boolean usable) {}

  public record CredentialStatus(boolean storageReady, Map<String, FieldStatus> fields) {}
}
