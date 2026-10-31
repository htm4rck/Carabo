package io.carabo.app.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CredentialCipher {
  @ConfigProperty(name = "carabo.secrets.base-path", defaultValue = "/run/secrets/carabo")
  String basePath;

  @ConfigProperty(
      name = "carabo.credentials.key-reference",
      defaultValue = "secret://tenant-credentials-key")
  String keyReference;

  private final SecureRandom random = new SecureRandom();

  public boolean available() {
    try {
      key();
      return true;
    } catch (RuntimeException error) {
      return false;
    }
  }

  private SecretKeySpec key() {
    if (keyReference == null
        || !keyReference.startsWith("secret://")
        || keyReference.startsWith("secret://managed/")) throw unavailable();
    try {
      String value = SecretReferenceFiles.read(basePath, keyReference);
      byte[] bytes =
          value.matches("[0-9a-fA-F]{64}")
              ? HexFormat.of().parseHex(value)
              : Base64.getDecoder().decode(value);
      if (bytes.length != 32) throw unavailable();
      return new SecretKeySpec(bytes, "AES");
    } catch (RuntimeException error) {
      throw unavailable();
    }
  }

  public String encrypt(UUID tenant, String field, String value) {
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
      cipher.updateAAD(aad(tenant, field));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      byte[] result = new byte[nonce.length + encrypted.length];
      System.arraycopy(nonce, 0, result, 0, nonce.length);
      System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
      return "v1:" + Base64.getEncoder().encodeToString(result);
    } catch (Exception error) {
      throw unavailable();
    }
  }

  public String decrypt(UUID tenant, String field, String value) {
    try {
      if (value == null || !value.startsWith("v1:")) throw unavailable();
      byte[] bytes = Base64.getDecoder().decode(value.substring(3));
      if (bytes.length < 28) throw unavailable();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 0, 12));
      cipher.updateAAD(aad(tenant, field));
      return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
    } catch (Exception error) {
      throw unavailable();
    }
  }

  private byte[] aad(UUID tenant, String field) {
    return ("carabo-credential:v1:" + tenant + ":" + field).getBytes(StandardCharsets.UTF_8);
  }

  private IllegalStateException unavailable() {
    return new IllegalStateException("El almacén seguro de credenciales no está disponible");
  }
}
