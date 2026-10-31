package io.carabo.app.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads platform secrets while refusing references outside the mounted directory. */
final class SecretReferenceFiles {
  private SecretReferenceFiles() {}

  static String read(String directory, String reference) {
    String name = reference.substring("secret://".length());
    if (name.isBlank()) throw new IllegalArgumentException("Referencia de secreto vacía");
    Path root = Path.of(directory).toAbsolutePath().normalize(),
        file = root.resolve(name).normalize();
    if (!file.startsWith(root))
      throw new IllegalArgumentException("Referencia de secreto fuera del almacén");
    try {
      if (!file.toRealPath().startsWith(root.toRealPath()))
        throw new IllegalArgumentException("Referencia de secreto fuera del almacén");
      return Files.readString(file, StandardCharsets.UTF_8).stripTrailing();
    } catch (java.io.IOException error) {
      throw new IllegalStateException("El almacén de secretos no está disponible");
    }
  }
}
