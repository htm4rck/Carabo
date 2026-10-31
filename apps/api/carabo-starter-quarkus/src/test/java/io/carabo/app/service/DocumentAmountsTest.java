package io.carabo.app.service;

import io.carabo.core.dto.EmitDocumentRequest;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;
import io.carabo.core.ubl.UblBuilder;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentAmountsTest {
    @Test
    void preservesPaidAmountsInTheDocumentAndXml() throws Exception {
        for (String[] grossAmounts : List.of(new String[]{"89", "8"}, new String[]{"79", "65", "8"}, new String[]{"325", "8"})) {
            List<EmitDocumentRequest.Line> lines = new ArrayList<>();
            BigDecimal expected = BigDecimal.ZERO;
            for (String gross : grossAmounts) {
                BigDecimal amount = new BigDecimal(gross);
                BigDecimal quantity = gross.equals("325") ? new BigDecimal("5") : BigDecimal.ONE;
                lines.add(line(quantity, amount.divide(quantity.multiply(new BigDecimal("1.18")), 14, RoundingMode.HALF_UP)));
                expected = expected.add(amount);
            }
            Document document = build(lines);
            assertEquals(0, expected.compareTo(document.getTotal()));
            assertEquals(0, document.getTotal().compareTo(document.getTaxBase().add(document.getIgvAmount())));
            String xml = new UblBuilder().build(document);
            var parsed = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            String payable = XPathFactory.newInstance().newXPath().evaluate("//*[local-name()='LegalMonetaryTotal']/*[local-name()='PayableAmount']", parsed);
            assertEquals(0, expected.compareTo(new BigDecimal(payable)));
        }
    }

    @Test
    void retainsTaxExclusivePricesAndDiscounts() {
        var line = line(new BigDecimal("2"), new BigDecimal("100"));
        line.setDiscount(new BigDecimal("10"));
        Document document = build(List.of(line));
        assertEquals(new BigDecimal("190.00"), document.getTaxBase());
        assertEquals(new BigDecimal("34.20"), document.getIgvAmount());
        assertEquals(new BigDecimal("224.20"), document.getTotal());
    }

    private EmitDocumentRequest.Line line(BigDecimal quantity, BigDecimal value) {
        var line = new EmitDocumentRequest.Line();
        line.setProductCode("TEST"); line.setProductName("Artículo de prueba");
        line.setUnitCode("NIU"); line.setQuantity(quantity); line.setUnitPrice(value); line.setAffectationCode("10");
        return line;
    }

    private Document build(List<EmitDocumentRequest.Line> lines) {
        var request = new EmitDocumentRequest();
        request.setBusinessCode("B20123456789"); request.setDocType("03"); request.setSeries("B001"); request.setNumber(1);
        request.setIssueDate(LocalDate.of(2026, 10, 31)); request.setCurrency("PEN"); request.setLines(lines);
        var receiver = new EmitDocumentRequest.Receiver(); receiver.setDocType("1"); receiver.setDoc("87654321"); receiver.setName("Cliente de prueba"); request.setReceiver(receiver);
        var tenant = new Tenant(); tenant.setIssuerRuc("20123456789"); tenant.setIssuerName("Empresa de prueba"); tenant.setIssuerAddress("Lima"); tenant.setIssuerUbigeo("150101");
        return new DocumentService().buildDocument(request, tenant);
    }
}
