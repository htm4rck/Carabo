package io.carabo.signer;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.security.Key;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

/** Independent XMLDSig validation using the certificate embedded in KeyInfo. */
public final class XmlSignatureVerifier {

    private XmlSignatureVerifier() {}

    public static VerificationResult verify(String signedXml) {
        try {
            Document document = parseSecurely(signedXml);
            NodeList signatures = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (signatures.getLength() != 1) {
                return new VerificationResult(false, null, "Se esperaba exactamente una firma XMLDSig");
            }

            CertificateKeySelector selector = new CertificateKeySelector();
            DOMValidateContext context = new DOMValidateContext(selector, signatures.item(0));
            context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
            XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
            boolean valid = signature.validate(context);
            X509Certificate certificate = selector.certificate;
            if (!valid) {
                return new VerificationResult(false, certificate, "Firma o referencias XMLDSig inválidas");
            }
            certificate.checkValidity(Date.from(Instant.now()));
            return new VerificationResult(true, certificate, null);
        } catch (Exception e) {
            return new VerificationResult(false, null, e.getMessage());
        }
    }

    private static Document parseSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    public record VerificationResult(boolean valid, X509Certificate certificate, String error) {}

    private static final class CertificateKeySelector extends KeySelector {
        private X509Certificate certificate;

        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method,
                                        XMLCryptoContext context) throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("La firma no contiene KeyInfo");
            }
            for (Object item : keyInfo.getContent()) {
                if (item instanceof X509Data data) {
                    for (Object value : data.getContent()) {
                        if (value instanceof X509Certificate found) {
                            if (!"RSA".equalsIgnoreCase(found.getPublicKey().getAlgorithm())) {
                                throw new KeySelectorException("Solo se admiten certificados RSA");
                            }
                            certificate = found;
                            Key key = found.getPublicKey();
                            return () -> key;
                        }
                    }
                }
            }
            throw new KeySelectorException("KeyInfo no contiene un certificado X.509");
        }
    }
}
