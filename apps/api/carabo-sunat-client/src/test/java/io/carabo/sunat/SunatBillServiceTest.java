package io.carabo.sunat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SunatBillServiceTest {
    private HttpServer server;
    private final AtomicReference<Response> next = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private SunatBillService client;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/billService", this::respond);
        server.start();
        client = new SunatBillService("http://localhost:" + server.getAddress().getPort() + "/billService", "20123456789MODDATOS", "secret");
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    @Test
    void parsesSunatCdrWhenZipStartsWithADirectory() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("dummy/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("R-test.xml"));
            zip.write(cdr("0", "Aceptado", null).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        String encoded = Base64.getEncoder().encodeToString(output.toByteArray());
        next.set(new Response(200, "<Envelope><applicationResponse>" + encoded + "</applicationResponse></Envelope>"));
        var result = client.sendBill("invoice", "<Invoice/>");
        assertTrue(result.accepted());
        assertEquals("0", result.responseCode());
    }

    @Test
    void parsesAcceptedCdrAndNamespacePrefixes() throws Exception {
        next.set(new Response(200, soap("applicationResponse", cdr("0", "Aceptado", null))));

        var result = client.sendBill("20123456789-01-F001-1", "<Invoice/>");

        assertTrue(result.accepted());
        assertFalse(result.observed());
        assertEquals("0", result.responseCode());
        assertTrue(result.cdrXml().contains("ApplicationResponse"));
        assertTrue(requestBody.get().contains("<fileName>20123456789-01-F001-1.zip</fileName>"));
    }

    @Test
    void preservesAcceptedWithObservationAsDistinctOutcome() throws Exception {
        next.set(new Response(200, soap("applicationResponse", cdr("0", "Aceptado", "Observación 4000"))));

        var result = client.sendBill("invoice", "<Invoice/>");

        assertTrue(result.accepted());
        assertTrue(result.observed());
        assertEquals("Observación 4000", result.observations().getFirst());
    }

    @Test
    void distinguishesFiscalRejectionFromRetryableTransportFailure() throws Exception {
        next.set(new Response(200, soap("applicationResponse", cdr("2335", "Documento rechazado", null))));
        var rejection = client.sendBill("invoice", "<Invoice/>");
        assertFalse(rejection.accepted());
        assertFalse(rejection.retryable());
        assertEquals("2335", rejection.responseCode());

        next.set(new Response(503, "maintenance"));
        var outage = client.sendBill("invoice", "<Invoice/>");
        assertFalse(outage.accepted());
        assertTrue(outage.retryable());
        assertEquals("HTTP_503", outage.responseCode());
    }

    @Test
    void representsTicket98AsProcessing() {
        next.set(new Response(200, """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body><getStatusResponse><status><statusCode>98</statusCode></status></getStatusResponse></soap:Body>
            </soap:Envelope>
            """));

        var result = client.getStatus("ticket-1");

        assertTrue(result.processing());
        assertFalse(result.accepted());
        assertEquals("98", result.responseCode());
    }

    @Test
    void parsesSoapFaultWithoutTreatingItAsFiscalCdr() {
        next.set(new Response(500, """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><soap:Fault>
              <faultcode>soap:Server</faultcode><faultstring>Servicio temporalmente no disponible</faultstring>
            </soap:Fault></soap:Body></soap:Envelope>
            """));

        var result = client.sendBill("invoice", "<Invoice/>");

        assertFalse(result.accepted());
        assertTrue(result.retryable());
        assertEquals("soap:Server", result.responseCode());
    }

    @Test
    void treatsCredentialFaultAsNonRetryableConfigurationError() {
        next.set(new Response(500, """
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><soap:Fault>
              <faultcode>soap:Client.0100</faultcode><faultstring>El usuario o clave SOL no es válido</faultstring>
            </soap:Fault></soap:Body></soap:Envelope>
            """));

        var result = client.sendBill("invoice", "<Invoice/>");

        assertFalse(result.retryable());
        assertEquals("soap:Client.0100", result.responseCode());
    }

    private void respond(HttpExchange exchange) {
        try (exchange) {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Response response = next.get();
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String soap(String tag, String cdr) throws Exception {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><sendBillResponse><"
            + tag + ">" + zipBase64(cdr) + "</" + tag + "></sendBillResponse></soap:Body></soap:Envelope>";
    }

    private static String cdr(String code, String description, String note) {
        return "<ar:ApplicationResponse xmlns:ar=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\" "
            + "xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
            + "<cbc:ResponseCode>" + code + "</cbc:ResponseCode><cbc:Description>" + description + "</cbc:Description>"
            + (note == null ? "" : "<cbc:Note>" + note + "</cbc:Note>") + "</ar:ApplicationResponse>";
    }

    private static String zipBase64(String content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("R-test.xml"));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private record Response(int status, String body) {}
}
