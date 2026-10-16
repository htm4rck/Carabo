package io.carabo.core.ubl;

import io.carabo.core.enums.DocumentType;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

/** Validates generated documents against the normative OASIS UBL 2.1 XSDs. */
public final class UblSchemaValidator {

    private static final Map<DocumentType, String> SCHEMAS = Map.of(
        DocumentType.INVOICE, "UBL-Invoice-2.1.xsd",
        DocumentType.RECEIPT, "UBL-Invoice-2.1.xsd",
        DocumentType.CREDIT_NOTE, "UBL-CreditNote-2.1.xsd",
        DocumentType.DEBIT_NOTE, "UBL-DebitNote-2.1.xsd"
    );

    private final Map<DocumentType, Schema> compiled = new EnumMap<>(DocumentType.class);

    public UblSchemaValidator() {
        SCHEMAS.forEach((type, file) -> compiled.put(type, compile(file)));
    }

    public void validate(String xml, DocumentType type) {
        Schema schema = compiled.get(type);
        if (schema == null) {
            throw new UblValidationException("No existe esquema UBL para " + type);
        }
        try {
            var validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException | IOException e) {
            throw new UblValidationException("XML UBL 2.1 inválido para " + type + ": " + e.getMessage(), e);
        }
    }

    private Schema compile(String file) {
        String resource = "ubl/2.1/xsd/maindoc/" + file;
        URL schemaUrl = UblSchemaValidator.class.getClassLoader().getResource(resource);
        if (schemaUrl == null) {
            throw new UblValidationException("No se encontró el esquema " + resource);
        }
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            // Imports are bundled beside the main schemas; network access remains forbidden.
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
            return factory.newSchema(schemaUrl);
        } catch (SAXException e) {
            throw new UblValidationException("No se pudo compilar el esquema " + resource, e);
        }
    }

    public static final class UblValidationException extends RuntimeException {
        public UblValidationException(String message) { super(message); }
        public UblValidationException(String message, Throwable cause) { super(message, cause); }
    }
}
