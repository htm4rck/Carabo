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

    @Test
    void preservesAllocatedOriginalCentsAcrossThreeCreditNotes() throws Exception {
        for (String[][] rows : List.of(new String[][]{{"0.0300000000","0.03","0.01"},{"0.0300000000","0.03","0.01"},{"0.0300000000","0.03","0.00"}},new String[][]{{"0.0266666667","0.03","0.00"},{"0.0266666667","0.03","0.00"},{"0.0266666667","0.02","0.01"}})) {
            BigDecimal totalNet=BigDecimal.ZERO,totalTax=BigDecimal.ZERO;
            for(String[] row:rows){
                var line=line(BigDecimal.ONE,new BigDecimal(row[0]));
                var original=new EmitDocumentRequest.OriginalAmounts();original.setNet(new BigDecimal(row[1]));original.setTax(new BigDecimal(row[2]));line.setOriginalAmounts(original);
                Document document=build(List.of(line),true);
                assertEquals(new BigDecimal(row[1]),document.getTaxBase());assertEquals(new BigDecimal(row[2]),document.getIgvAmount());
                assertEquals(new BigDecimal(row[0]),document.getLines().get(0).getUnitPrice());
                var parsed=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(new UblBuilder().build(document).getBytes(StandardCharsets.UTF_8)));
                var xpath=XPathFactory.newInstance().newXPath();
                assertEquals(0,new BigDecimal(row[1]).compareTo(new BigDecimal(xpath.evaluate("//*[local-name()='CreditNoteLine']/*[local-name()='LineExtensionAmount']",parsed))));
                assertEquals(0,new BigDecimal(row[2]).compareTo(new BigDecimal(xpath.evaluate("//*[local-name()='CreditNoteLine']/*[local-name()='TaxTotal']/*[local-name()='TaxAmount']",parsed))));
                totalNet=totalNet.add(document.getTaxBase());totalTax=totalTax.add(document.getIgvAmount());
            }
            assertEquals(new BigDecimal(rows[0][0].equals("0.0300000000")?"0.09":"0.08"),totalNet);
            assertEquals(new BigDecimal(rows[0][0].equals("0.0300000000")?"0.02":"0.01"),totalTax);
        }
    }

    @Test
    void cannotApplyOriginalCreditCentsToInvoicesOrExceedOneCent() {
        var line=line(BigDecimal.ONE,new BigDecimal("0.03"));var original=new EmitDocumentRequest.OriginalAmounts();original.setNet(new BigDecimal("0.03"));original.setTax(new BigDecimal("0.00"));line.setOriginalAmounts(original);
        assertThrows(jakarta.ws.rs.BadRequestException.class,()->build(List.of(line)));
        original.setNet(new BigDecimal("0.05"));assertThrows(jakarta.ws.rs.BadRequestException.class,()->build(List.of(line),true));
    }

    private EmitDocumentRequest.Line line(BigDecimal quantity, BigDecimal value) {
        var line = new EmitDocumentRequest.Line();
        line.setProductCode("TEST"); line.setProductName("Artículo de prueba");
        line.setUnitCode("NIU"); line.setQuantity(quantity); line.setUnitPrice(value); line.setAffectationCode("10");
        return line;
    }

    private Document build(List<EmitDocumentRequest.Line> lines) {return build(lines,false);}

    private Document build(List<EmitDocumentRequest.Line> lines,boolean credit) {
        var request = new EmitDocumentRequest();
        request.setBusinessCode("B20123456789"); request.setDocType("03"); request.setSeries("B001"); request.setNumber(1);
        request.setIssueDate(LocalDate.of(2026, 10, 31)); request.setCurrency("PEN"); request.setLines(lines);
        var receiver = new EmitDocumentRequest.Receiver(); receiver.setDocType("1"); receiver.setDoc("87654321"); receiver.setName("Cliente de prueba"); request.setReceiver(receiver);
        if(credit){request.setDocType("07");request.setSeries("BC01");var reference=new EmitDocumentRequest.Reference();reference.setDocType("03");reference.setSeries("B001");reference.setNumber(1);reference.setReasonCode("07");reference.setReason("Devolución parcial original");request.setReference(reference);}
        var tenant = new Tenant(); tenant.setIssuerRuc("20123456789"); tenant.setIssuerName("Empresa de prueba"); tenant.setIssuerAddress("Lima"); tenant.setIssuerUbigeo("150101");
        return new DocumentService().buildDocument(request, tenant);
    }
}
