package io.carabo.app.service;

import com.sun.net.httpserver.HttpServer;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentType;
import io.carabo.sunat.SunatBillService;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchServiceXmlTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void summaryIdOmitsIssuerRucWhileZipAndXmlFilenamesRetainIt() throws Exception {
        var service = service();
        var tenant = tenant();
        var document = document(DocumentType.RECEIPT, "B001");
        String filename = "20123456789-RC-20261028-2";
        String xml = service.buildSummaryXml(tenant, List.of(document), TODAY, filename);

        assertEquals("RC-20261028-2", text(xml, "/*/*[local-name()='ID']"));
        assertEquals("20123456789", text(xml, "//*[local-name()='AccountingSupplierParty']/*[local-name()='CustomerAssignedAccountID']"));
        assertEquals(document.getFullNumber(), text(xml, "//*[local-name()='SummaryDocumentsLine']/*[local-name()='ID']"));
        assertArchiveMatchesDocumentId(filename, xml);
    }

    @Test
    void summaryPreservesReceiptDateSeparatelyFromGenerationDate() throws Exception {
        var document = document(DocumentType.RECEIPT, "B001");
        document.setIssueDate(TODAY.minusDays(1));
        String xml = service().buildSummaryXml(tenant(), List.of(document), document.getIssueDate(),
            "20123456789-RC-20261028-3");

        assertEquals("2026-10-27", text(xml, "/*/*[local-name()='ReferenceDate']"));
        assertEquals("2026-10-28", text(xml, "/*/*[local-name()='IssueDate']"));
        assertEquals("RC-20261028-3", text(xml, "/*/*[local-name()='ID']"));
    }

    @Test
    void summaryIncludesIgvRateAndReportsTaxBaseWithoutIncludingIgv() throws Exception {
        var document = document(DocumentType.RECEIPT, "B001");
        document.setTaxBase(new BigDecimal("82.20"));
        document.setIgvAmount(new BigDecimal("14.80"));
        document.setTotal(new BigDecimal("97.00"));
        String xml = service().buildSummaryXml(tenant(), List.of(document), TODAY, "20123456789-RC-20261028-5");

        assertEquals("18.00", text(xml, "//*[local-name()='TaxCategory']/*[local-name()='Percent']"));
        assertEquals("82.20", text(xml, "//*[local-name()='BillingPayment']/*[local-name()='PaidAmount']"));
        assertEquals("01", text(xml, "//*[local-name()='BillingPayment']/*[local-name()='InstructionID']"));
        assertEquals("97.00", text(xml, "//*[local-name()='SummaryDocumentsLine']/*[local-name()='TotalAmount']"));
        assertEquals("14.80", text(xml, "//*[local-name()='TaxSubtotal']/*[local-name()='TaxAmount']"));
    }

    @Test
    void voidedCommunicationUsesTheSameIdAndFilenameContract() throws Exception {
        String filename = "20123456789-RA-20261028-4";
        String xml = service().buildVoidedXml(tenant(), List.of(document(DocumentType.INVOICE, "F001")),
            TODAY, filename, "Error & corrección");

        assertEquals("RA-20261028-4", text(xml, "/*/*[local-name()='ID']"));
        assertEquals("Error & corrección", text(xml, "//*[local-name()='VoidReasonDescription']"));
        assertArchiveMatchesDocumentId(filename, xml);
    }

    private static void assertArchiveMatchesDocumentId(String filename, String xml) throws Exception {
        var request = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = "<Envelope><ticket>test-ticket</ticket></Envelope>".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            var client = new SunatBillService("http://127.0.0.1:" + server.getAddress().getPort(), "test-user", "test-password");
            assertEquals("test-ticket", client.sendSummary(filename, xml));
            assertEquals(filename + ".zip", text(request.get(), "//*[local-name()='fileName']"));
            byte[] zipped = Base64.getDecoder().decode(text(request.get(), "//*[local-name()='contentFile']"));
            try (var zip = new ZipInputStream(new ByteArrayInputStream(zipped))) {
                assertEquals(filename + ".xml", zip.getNextEntry().getName());
                String archived = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(xml, archived);
                assertEquals(filename, "20123456789-" + text(archived, "/*/*[local-name()='ID']"));
                assertNull(zip.getNextEntry());
            }
        } finally { server.stop(0); }
    }

    private static String text(String xml, String expression) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var parsed = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return XPathFactory.newInstance().newXPath().evaluate(expression, parsed);
    }

    private static BatchService service() {
        var service = new BatchService();
        service.fiscalClock = new FiscalClock("America/Lima") {
            @Override public LocalDate today() { return TODAY; }
        };
        return service;
    }

    private static Tenant tenant() {
        var tenant = new Tenant();
        tenant.setIssuerRuc("20123456789");
        tenant.setIssuerName("Emisor de prueba");
        return tenant;
    }

    private static Document document(DocumentType type, String series) {
        var document = new Document();
        document.setDocType(type);
        document.setSeries(series);
        document.setNumber(1);
        document.setIssueDate(TODAY);
        document.setReceiverDoc("12345678");
        document.setReceiverDocType("1");
        return document;
    }
}
