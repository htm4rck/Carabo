package io.carabo.core.ubl;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentType;
import io.carabo.core.validation.DocumentValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UblSchemaValidationTest {

    @Test
    void generatedDocumentsPassNormativeSchemasForAllSupportedTypes() {
        for (DocumentType type : new DocumentType[]{
            DocumentType.INVOICE, DocumentType.RECEIPT,
            DocumentType.CREDIT_NOTE, DocumentType.DEBIT_NOTE
        }) {
            Document document = document(type);
            String xml = assertDoesNotThrow(() -> new UblBuilder().build(document), type.name());
            if (type == DocumentType.CREDIT_NOTE) {
                assertTrue(xml.contains("<cbc:CreditedQuantity"));
            }
            if (type == DocumentType.DEBIT_NOTE) {
                assertTrue(xml.contains("<cbc:DebitedQuantity"));
                assertTrue(xml.contains("<cac:RequestedMonetaryTotal>"));
            }
        }
    }

    @Test
    void malformedDocumentIsRejectedWithSchemaEvidence() {
        var validator = new UblSchemaValidator();
        assertThrows(UblSchemaValidator.UblValidationException.class,
            () -> validator.validate("<Invoice/>", DocumentType.INVOICE));
    }

    @Test
    void typeSpecificFiscalRulesAcceptValidFamiliesAndRejectWrongNoteCatalog() {
        Tenant tenant = new Tenant();
        tenant.setBusinessCode("B20999999990");
        tenant.setActive(true);
        tenant.setCertificateFile("test.p12");
        DocumentValidator validator = new DocumentValidator();

        for (DocumentType type : new DocumentType[]{DocumentType.INVOICE, DocumentType.RECEIPT,
            DocumentType.CREDIT_NOTE, DocumentType.DEBIT_NOTE}) {
            assertTrue(validator.validate(document(type), tenant).isEmpty(), type.name());
        }

        Document invalidDebitNote = document(DocumentType.DEBIT_NOTE);
        invalidDebitNote.setRefReasonCode("13");
        assertTrue(validator.validate(invalidDebitNote, tenant).stream()
            .anyMatch(error -> error.contains("catálogo SUNAT 10")));
    }

    private Document document(DocumentType type) {
        Document doc = new Document();
        doc.setDocType(type);
        doc.setSeries(switch (type) {
            case INVOICE -> "F001";
            case RECEIPT -> "B001";
            case CREDIT_NOTE -> "FC01";
            case DEBIT_NOTE -> "FD01";
            default -> throw new IllegalArgumentException();
        });
        doc.setNumber(1);
        doc.setIssueDate(LocalDate.now(ZoneId.of("America/Lima")));
        doc.setCurrency("PEN");
        doc.setIssuerRuc("20999999990");
        doc.setIssuerName("Carabo Test S.A.C.");
        doc.setReceiverDocType(type == DocumentType.RECEIPT ? "1" : "6");
        doc.setReceiverDoc(type == DocumentType.RECEIPT ? "12345678" : "20111111111");
        doc.setReceiverName("Cliente Test");
        doc.setTaxBase(new BigDecimal("100.00"));
        doc.setIgvAmount(new BigDecimal("18.00"));
        doc.setIscAmount(BigDecimal.ZERO);
        doc.setTotal(new BigDecimal("118.00"));

        if (type == DocumentType.CREDIT_NOTE || type == DocumentType.DEBIT_NOTE) {
            doc.setRefDocType("01");
            doc.setRefSeries("F001");
            doc.setRefNumber(42);
            doc.setRefReasonCode("01");
            doc.setRefReason("Ajuste de comprobante");
        }

        DocumentLine line = new DocumentLine();
        line.setDocument(doc);
        line.setLineNumber(1);
        line.setUnitCode("NIU");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setGrossPrice(new BigDecimal("118.00"));
        line.setTaxBase(new BigDecimal("100.00"));
        line.setIgvAmount(new BigDecimal("18.00"));
        line.setIgvRate(new BigDecimal("18.00"));
        line.setAffectationCode("10");
        line.setProductName("Servicio de prueba");
        doc.getLines().add(line);
        return doc;
    }
}
