package io.carabo.signer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class XmlSignerTest {

    private static final String PASSWORD = "changeit";
    private static final String XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
          xmlns:ext="urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2">
          <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent><placeholder xmlns="urn:test"/></ext:ExtensionContent></ext:UBLExtension></ext:UBLExtensions>
          <ID xmlns="urn:test">F001-00000001</ID>
        </Invoice>
        """;

    @TempDir
    Path tempDir;

    @Test
    void signsAndIndependentlyValidatesAndDetectsTampering() throws Exception {
        Path store = createStore("valid.p12", "signing", null, 365);
        XmlSigner signer;
        try (InputStream input = Files.newInputStream(store)) {
            signer = new XmlSigner(input, PASSWORD);
        }

        XmlSigner.SignResult result = signer.sign(XML);
        assertEquals("signing", signer.certificateAlias());
        assertNotNull(result.digestValue());
        assertTrue(XmlSignatureVerifier.verify(result.signedXml()).valid());
        assertFalse(XmlSignatureVerifier.verify(
            result.signedXml().replace("F001-00000001", "F001-99999999")).valid());
    }

    @Test
    void requiresExplicitAliasWhenStoreHasMultiplePrivateKeys() throws Exception {
        Path store = createStore("multiple.p12", "first", null, 365);
        addKey(store, "second", null, 365);

        try (InputStream input = Files.newInputStream(store)) {
            assertThrows(XmlSigner.SignerException.class, () -> new XmlSigner(input, PASSWORD));
        }
        try (InputStream input = Files.newInputStream(store)) {
            assertEquals("second", new XmlSigner(input, PASSWORD, "second").certificateAlias());
        }
    }

    @Test
    void rejectsExpiredCertificateBeforeSigning() throws Exception {
        Path store = createStore("expired.p12", "expired", "2020/01/01 00:00:00", 1);
        try (InputStream input = Files.newInputStream(store)) {
            assertThrows(XmlSigner.SignerException.class, () -> new XmlSigner(input, PASSWORD));
        }
    }

    @Test
    void secureParserRejectsDoctype() throws Exception {
        Path store = createStore("xxe.p12", "signing", null, 365);
        try (InputStream input = Files.newInputStream(store)) {
            XmlSigner signer = new XmlSigner(input, PASSWORD);
            assertThrows(XmlSigner.SignerException.class,
                () -> signer.sign("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]>" + XML));
        }
    }

    private Path createStore(String name, String alias, String startDate, int validity) throws Exception {
        Path store = tempDir.resolve(name);
        addKey(store, alias, startDate, validity);
        return store;
    }

    private void addKey(Path store, String alias, String startDate, int validity) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(java.util.List.of("-genkeypair", "-alias", alias, "-keyalg", "RSA",
            "-keysize", "2048", "-dname", "CN=Carabo Test,O=Tordo,C=PE",
            "-storetype", "PKCS12", "-keystore", store.toString(),
            "-storepass", PASSWORD, "-keypass", PASSWORD, "-validity", Integer.toString(validity), "-noprompt"));
        if (startDate != null) {
            command.add("-startdate");
            command.add(startDate);
        }
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
    }
}
