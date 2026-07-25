package io.carabo.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Stores electronic document artifacts (signed XML, CDR, PDF).
 * Default implementation: local filesystem.
 * Structure: {basePath}/{businessCode}/{series}/{number}/
 *   - signed.xml
 *   - cdr.xml
 *   - representation.pdf
 */
public class DocumentStorage {

    private final Path basePath;

    public DocumentStorage(String basePath) {
        this.basePath = Path.of(basePath);
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
        return readString(resolvePath(businessCode, series, number, "signed.xml"));
    }

    public Optional<String> getCdr(String businessCode, String series, int number) {
        return readString(resolvePath(businessCode, series, number, "cdr.xml"));
    }

    public Optional<byte[]> getPdf(String businessCode, String series, int number) {
        return readBytes(resolvePath(businessCode, series, number, "representation.pdf"));
    }

    public boolean exists(String businessCode, String series, int number, String fileName) {
        return Files.exists(resolvePath(businessCode, series, number, fileName));
    }

    private Path resolvePath(String businessCode, String series, int number, String fileName) {
        return basePath.resolve(businessCode).resolve(series).resolve(String.valueOf(number)).resolve(fileName);
    }

    private void write(Path path, byte[] content) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to write: " + path, e);
        }
    }

    private Optional<String> readString(Path path) {
        try {
            if (!Files.exists(path)) return Optional.empty();
            return Optional.of(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new StorageException("Failed to read: " + path, e);
        }
    }

    private Optional<byte[]> readBytes(Path path) {
        try {
            if (!Files.exists(path)) return Optional.empty();
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new StorageException("Failed to read: " + path, e);
        }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String msg, Throwable cause) { super(msg, cause); }
    }
}
