package io.carabo.core.validation;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;
import io.carabo.core.entity.DocumentTax;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentType;
import io.carabo.core.ubl.UblBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PeruFiscalGoldenTest {

    @Test
    void mixedTaxableAndExemptInvoiceReconcilesAndProducesCatalog07Ubl() {
        Document document = baseInvoice();
        addLine(document, 1, "Producto gravado", "10", "100.00", "18.00");
        addLine(document, 2, "Producto exonerado", "20", "50.00", "0.00");
        addTax(document, "1000", "IGV", "100.00", "18.00", "18.00");
        addTax(document, "9997", "EXO", "50.00", "0.00", "0.00");
        document.setTaxBase(new BigDecimal("150.00"));
        document.setIgvAmount(new BigDecimal("18.00"));
        document.setTotal(new BigDecimal("168.00"));

        Tenant tenant = new Tenant();
        tenant.setBusinessCode("B20999999990");
        tenant.setActive(true);
        tenant.setCertificateFile("test.p12");

        assertTrue(new DocumentValidator().validate(document, tenant).isEmpty());

        String xml = new UblBuilder().build(document);
        assertTrue(xml.contains("<cbc:ID>1000</cbc:ID>"));
        assertTrue(xml.contains("<cbc:ID>9997</cbc:ID>"));
        assertTrue(xml.contains("schemeID=\"UN/ECE 5305\">E</cbc:ID>"));
        assertTrue(xml.contains(">168.00</cbc:PayableAmount>"));
    }

    @Test
    void unsupportedAffectationIsRejectedInsteadOfSilentlyTaxed() {
        Document document = baseInvoice();
        addLine(document, 1, "Caso no implementado", "17", "100.00", "0.00");
        document.setTaxBase(new BigDecimal("100.00"));
        document.setIgvAmount(BigDecimal.ZERO);
        document.setTotal(new BigDecimal("100.00"));

        Tenant tenant = new Tenant();
        tenant.setBusinessCode("B20999999990");
        tenant.setActive(true);
        tenant.setCertificateFile("test.p12");

        assertTrue(new DocumentValidator().validate(document, tenant).stream()
            .anyMatch(error -> error.contains("afectación IGV no soportado")));
    }

    private Document baseInvoice() {
        Document document = new Document();
        document.setBusinessCode("B20999999990");
        document.setDocType(DocumentType.INVOICE);
        document.setSeries("F001");
        document.setNumber(1);
        document.setIssueDate(LocalDate.now());
        document.setCurrency("PEN");
        document.setIssuerRuc("20999999990");
        document.setIssuerName("Carabo Test");
        document.setReceiverDocType("6");
        document.setReceiverDoc("20111111111");
        document.setReceiverName("Cliente Test");
        return document;
    }

    private void addLine(Document document, int number, String name, String affectation,
                         String base, String tax) {
        DocumentLine line = new DocumentLine();
        line.setDocument(document);
        line.setLineNumber(number);
        line.setProductName(name);
        line.setUnitCode("NIU");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(base));
        line.setGrossPrice(new BigDecimal(base).add(new BigDecimal(tax)));
        line.setDiscount(BigDecimal.ZERO);
        line.setTaxBase(new BigDecimal(base));
        line.setIgvAmount(new BigDecimal(tax));
        var profile = PeruTaxRules.profile(affectation);
        line.setIgvRate(profile != null ? profile.rate() : BigDecimal.ZERO);
        line.setAffectationCode(affectation);
        line.setLineTotal(new BigDecimal(base).add(new BigDecimal(tax)));
        document.getLines().add(line);
    }

    private void addTax(Document document, String code, String name, String base,
                        String amount, String rate) {
        DocumentTax tax = new DocumentTax();
        tax.setDocument(document);
        tax.setTaxCode(code);
        tax.setTaxName(name);
        tax.setTaxBase(new BigDecimal(base));
        tax.setTaxAmount(new BigDecimal(amount));
        tax.setTaxRate(new BigDecimal(rate));
        document.getTaxes().add(tax);
    }
}
