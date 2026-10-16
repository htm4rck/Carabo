package io.carabo.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageTest {
    private static final String KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    @TempDir Path root;

    @Test
    void encryptsAuthenticatesAndChecksumsNewArtifacts() throws Exception {
        DocumentStorage storage = new DocumentStorage(root.toString(), KEY);
        storage.storeSignedXml("B20999999990", "F001", 1, "<Invoice>secreto</Invoice>");
        Path artifact = root.resolve("B20999999990/F001/1/signed.xml");
        assertFalse(Files.readString(artifact, StandardCharsets.ISO_8859_1).contains("secreto"));
        assertTrue(Files.isRegularFile(artifact.resolveSibling("signed.xml.sha256")));
        assertEquals("<Invoice>secreto</Invoice>", storage.getSignedXml("B20999999990", "F001", 1).orElseThrow());
        assertTrue(storage.verify("B20999999990", "F001", 1, "signed.xml"));
    }

    @Test
    void rejectsTamperingAndCrossRootPaths() throws Exception {
        DocumentStorage storage = new DocumentStorage(root.toString(), KEY);
        storage.storeCdr("B20999999990", "F001", 2, "<CDR>aceptado</CDR>");
        Files.writeString(root.resolve("B20999999990/F001/2/cdr.xml"), "alterado");
        assertThrows(DocumentStorage.StorageException.class, () -> storage.getCdr("B20999999990", "F001", 2));
        assertFalse(storage.verify("B20999999990", "F001", 2, "cdr.xml"));
        assertThrows(IllegalArgumentException.class, () -> storage.getSignedXml("../otro-tenant", "F001", 1));
    }

    @Test
    void readsLegacyPlaintextDuringControlledMigration() throws Exception {
        Path legacy = root.resolve("B20999999990/F001/3/signed.xml");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "<Invoice>legacy</Invoice>");
        DocumentStorage storage = new DocumentStorage(root.toString(), KEY);
        assertEquals("<Invoice>legacy</Invoice>", storage.getSignedXml("B20999999990", "F001", 3).orElseThrow());
    }
}
