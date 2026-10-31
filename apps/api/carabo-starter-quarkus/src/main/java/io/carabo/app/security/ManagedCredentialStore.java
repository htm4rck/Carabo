package io.carabo.app.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ManagedCredentialStore {
  public static final String PREFIX = "secret://managed/";
  private static final Set<String> FIELDS =
      Set.of("solPassword", "greClientSecret", "oseApiToken", "certificatePassword");
  @Inject EntityManager em;
  @Inject CredentialCipher cipher;

  public boolean available() {
    return cipher.available();
  }

  public Prepared prepare(UUID tenant, String field, String value) {
    if (!FIELDS.contains(field)) throw new IllegalArgumentException("Campo de credencial inválido");
    return new Prepared(UUID.randomUUID(), tenant, field, cipher.encrypt(tenant, field, value));
  }

  public String persist(Prepared value, String actor) {
    em.createNativeQuery(
            "INSERT INTO"
                + " carabo.tenant_credential(id,tenant_id,credential_name,encrypted_value,updated_by)"
                + " VALUES(?1,?2,?3,?4,?5)")
        .setParameter(1, value.id())
        .setParameter(2, value.tenant())
        .setParameter(3, value.field())
        .setParameter(4, value.encrypted())
        .setParameter(5, actor)
        .executeUpdate();
    return value.reference();
  }

  public boolean belongsTo(String reference, UUID tenant, String field) {
    if (reference == null || !reference.startsWith(PREFIX)) return false;
    return !em.createNativeQuery(
            "SELECT 1 FROM carabo.tenant_credential WHERE id=?1 AND tenant_id=?2 AND"
                + " credential_name=?3")
        .setParameter(1, id(reference))
        .setParameter(2, tenant)
        .setParameter(3, field)
        .getResultList()
        .isEmpty();
  }

  public String resolve(String reference) {
    var rows =
        em.createNativeQuery(
                "SELECT tenant_id,credential_name,encrypted_value FROM carabo.tenant_credential"
                    + " WHERE id=?1")
            .setParameter(1, id(reference))
            .getResultList();
    if (rows.isEmpty())
      throw new IllegalStateException("La credencial guardada no está disponible");
    Object[] row = (Object[]) rows.getFirst();
    return cipher.decrypt(UUID.fromString(row[0].toString()), row[1].toString(), row[2].toString());
  }

  private UUID id(String reference) {
    try {
      return UUID.fromString(reference.substring(PREFIX.length()));
    } catch (RuntimeException error) {
      throw new IllegalArgumentException("Referencia de credencial inválida");
    }
  }

  public record Prepared(UUID id, UUID tenant, String field, String encrypted) {
    public String reference() {
      return PREFIX + id;
    }
  }
}
