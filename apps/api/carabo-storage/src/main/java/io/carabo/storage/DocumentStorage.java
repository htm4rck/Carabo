package io.carabo.storage;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

/** Tenant-segmented storage with optional AES-256-GCM and SHA-256 sidecars. */
public class DocumentStorage {
    private static final byte[] MAGIC = "CARABO1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path basePath;
    private final SecretKeySpec encryptionKey;

    public DocumentStorage(String basePath) { this(basePath, null); }

    /** Accepts a 64-character hex key or a Base64-encoded 32-byte key. */
    public DocumentStorage(String basePath, String encryptionKey) {
        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
        this.encryptionKey = parseKey(encryptionKey);
    }

    public void storeSignedXml(String businessCode, String series, int number, String xml) {
        write(resolvePath(businessCode, series, number, "signed.xml"), xml.getBytes(StandardCharsets.UTF_8));
    }

    public void storeCdr(String businessCode, String series, int number, String cdrXml) {
        write(resolvePath(businessCode, series, number, "cdr.xml"), cdrXml.getBytes(StandardCharsets.UTF_8));
    }

    public void storePdf(String businessCode, String series, int number, byte[] pdf) {
        write(resolvePath(businessCode, series, number, "representation.pdf"), pdf);
    }

    public Optional<String> getSignedXml(String businessCode, String series, int number) {
        return readBytes(resolvePath(businessCode, series, number, "signed.xml"))
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    public Optional<String> getCdr(String businessCode, String series, int number) {
        return readBytes(resolvePath(businessCode, series, number, "cdr.xml"))
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    public Optional<byte[]> getPdf(String businessCode, String series, int number) {
        return readBytes(resolvePath(businessCode, series, number, "representation.pdf"));
    }

    public boolean exists(String businessCode, String series, int number, String fileName) {
        return Files.isRegularFile(resolvePath(businessCode, series, number, fileName));
    }

    public boolean verify(String businessCode, String series, int number, String fileName) {
        Path path = resolvePath(businessCode, series, number, fileName);
        if (!Files.isRegularFile(path)) return false;
        try {
            byte[] stored = Files.readAllBytes(path);
            verifyChecksum(path, stored);
            decode(stored);
            return true;
        } catch (RuntimeException | IOException e) {
            return false;
        }
    }

    private Path resolvePath(String businessCode, String series, int number, String fileName) {
        String safeBusinessCode = safeSegment(businessCode, "businessCode");
        String safeSeries = safeSegment(series, "series");
        String safeFileName = safeSegment(fileName, "fileName");
        if (number <= 0) throw new IllegalArgumentException("number must be positive");
        Path resolved = basePath.resolve(safeBusinessCode).resolve(safeSeries)
            .resolve(String.valueOf(number)).resolve(safeFileName).normalize();
        if (!resolved.startsWith(basePath)) throw new IllegalArgumentException("Artifact path escapes storage root");
        return resolved;
    }

    private String safeSegment(String value, String field) {
        if (value == null || value.isBlank() || value.equals(".") || value.equals("..")
            || !value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(field + " contains unsafe path characters");
        }
        return value;
    }

    private void write(Path path, byte[] content) {
        try {
            Files.createDirectories(path.getParent());
            byte[] stored = encode(content);
            atomicWrite(path, stored);
            atomicWrite(checksumPath(path), (sha256(stored) + "\n").getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new StorageException("Failed to write artifact", e);
        }
    }

    private Optional<byte[]> readBytes(Path path) {
        try {
            if (!Files.isRegularFile(path)) return Optional.empty();
            byte[] stored = Files.readAllBytes(path);
            verifyChecksum(path, stored);
            return Optional.of(decode(stored));
        } catch (IOException e) {
            throw new StorageException("Failed to read artifact", e);
        }
    }

    private byte[] encode(byte[] plaintext) {
        if (encryptionKey == null) return plaintext;
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] encoded = new byte[MAGIC.length + iv.length + ciphertext.length];
            System.arraycopy(MAGIC, 0, encoded, 0, MAGIC.length);
            System.arraycopy(iv, 0, encoded, MAGIC.length, iv.length);
            System.arraycopy(ciphertext, 0, encoded, MAGIC.length + iv.length, ciphertext.length);
            return encoded;
        } catch (Exception e) {
            throw new StorageException("Failed to encrypt artifact", e);
        }
    }

    private byte[] decode(byte[] stored) {
        if (!isEncrypted(stored)) return stored;
        if (encryptionKey == null) throw new StorageException("Encrypted artifact requires the storage key", null);
        try {
            byte[] iv = Arrays.copyOfRange(stored, MAGIC.length, MAGIC.length + IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(stored, MAGIC.length + IV_BYTES, stored.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new StorageException("Artifact authentication or decryption failed", e);
        }
    }

    private boolean isEncrypted(byte[] value) {
        return value.length >= MAGIC.length + IV_BYTES + 16
            && Arrays.equals(MAGIC, Arrays.copyOf(value, MAGIC.length));
    }

    private void verifyChecksum(Path path, byte[] stored) throws IOException {
        Path checksum = checksumPath(path);
        if (!Files.isRegularFile(checksum)) return; // Legacy record awaiting controlled migration.
        String expected = Files.readString(checksum, StandardCharsets.US_ASCII).trim();
        String actual = sha256(stored);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII))) {
            throw new StorageException("Artifact checksum mismatch", null);
        }
    }

    private Path checksumPath(Path path) { return path.resolveSibling(path.getFileName() + ".sha256"); }

    private void atomicWrite(Path target, byte[] value) throws IOException {
        Path staged = Files.createTempFile(target.getParent(), ".carabo-", ".tmp");
        try {
            Files.write(staged, value, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static SecretKeySpec parseKey(String configured) {
        if (configured == null || configured.isBlank()) return null;
        try {
            byte[] decoded = configured.matches("[0-9a-fA-F]{64}")
                ? HexFormat.of().parseHex(configured) : java.util.Base64.getDecoder().decode(configured);
            if (decoded.length != 32) throw new IllegalArgumentException("wrong length");
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Storage encryption key must be 64 hex characters or Base64 for 32 bytes", e);
        }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) { super(message, cause); }
    }
}
