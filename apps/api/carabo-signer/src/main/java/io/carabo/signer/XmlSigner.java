package io.carabo.signer;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Signs UBL 2.1 XML documents with a PKCS12 (.pfx/.p12) certificate.
 * Produces an enveloped XMLDSig signature inside ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent.
 */
public class XmlSigner {

    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    public XmlSigner(InputStream pfxStream, String password) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(pfxStream, password.toCharArray());
            String alias = ks.aliases().nextElement();
            this.privateKey = (PrivateKey) ks.getKey(alias, password.toCharArray());
            this.certificate = (X509Certificate) ks.getCertificate(alias);
        } catch (Exception e) {
            throw new SignerException("Failed to load certificate", e);
        }
    }

    public XmlSigner(PrivateKey privateKey, X509Certificate certificate) {
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    /**
     * Signs the XML string and returns the signed XML string.
     * Also returns the digest hash (SHA-256 of the signed info) for SUNAT hash_code.
     */
    public SignResult sign(String xml) {
        try {
            Document doc = parseXml(xml);
            Node extensionContent = findExtensionContent(doc);

            // Clear placeholder content
            while (extensionContent.hasChildNodes()) {
                extensionContent.removeChild(extensionContent.getFirstChild());
            }

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

            // Reference: enveloped transform over entire document
            Reference ref = fac.newReference("",
                fac.newDigestMethod(DigestMethod.SHA256, null),
                List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
                null, null);

            // SignedInfo
            SignedInfo signedInfo = fac.newSignedInfo(
                fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                fac.newSignatureMethod("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null),
                Collections.singletonList(ref));

            // KeyInfo with X509 certificate
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            X509Data x509Data = kif.newX509Data(Collections.singletonList(certificate));
            KeyInfo keyInfo = kif.newKeyInfo(Collections.singletonList(x509Data));

            // Sign
            DOMSignContext signContext = new DOMSignContext(privateKey, extensionContent);
            signContext.setDefaultNamespacePrefix("ds");

            XMLSignature signature = fac.newXMLSignature(signedInfo, keyInfo);
            signature.sign(signContext);

            // Compute hash for SUNAT
            String digestValue = computeHash(doc);

            return new SignResult(serializeXml(doc), digestValue);
        } catch (Exception e) {
            throw new SignerException("XML signing failed", e);
        }
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private Node findExtensionContent(Document doc) {
        // Find ext:ExtensionContent inside ext:UBLExtensions
        NodeList nodes = doc.getElementsByTagNameNS(
            "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2",
            "ExtensionContent");
        if (nodes.getLength() == 0) {
            throw new SignerException("No ext:ExtensionContent found in XML");
        }
        return nodes.item(0);
    }

    private String computeHash(Document doc) throws Exception {
        // Hash = Base64(SHA-256(SignatureValue))
        NodeList sigValues = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "SignatureValue");
        if (sigValues.getLength() > 0) {
            String sigValue = sigValues.item(0).getTextContent().trim();
            byte[] decoded = Base64.getDecoder().decode(sigValue.replaceAll("\\s+", ""));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(decoded));
        }
        return null;
    }

    private String serializeXml(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty("omit-xml-declaration", "no");
        transformer.setOutputProperty("encoding", "UTF-8");
        transformer.setOutputProperty("indent", "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    public record SignResult(String signedXml, String digestValue) {}

    public static class SignerException extends RuntimeException {
        public SignerException(String msg) { super(msg); }
        public SignerException(String msg, Throwable cause) { super(msg, cause); }
    }
}
