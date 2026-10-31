package io.carabo.sunat.gre;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SunatGreClientTest {
    HttpServer server;
    SunatGreClient client;
    final ObjectMapper mapper = new ObjectMapper();
    final AtomicReference<String> response = new AtomicReference<>("{}");
    final AtomicReference<String> body = new AtomicReference<>();
    final AtomicInteger status = new AtomicInteger(200), tokenStatus = new AtomicInteger(200), submissions = new AtomicInteger();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            try (exchange) { byte[] bytes = "{\"access_token\":\"test-only\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(tokenStatus.get(), bytes.length); exchange.getResponseBody().write(bytes); }
        });
        server.createContext("/gre/", exchange -> {
            try (exchange) {
                if (exchange.getRequestMethod().equals("POST")) submissions.incrementAndGet();
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status.get(), bytes.length); exchange.getResponseBody().write(bytes);
            }
        });
        server.start(); String base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new SunatGreClient("test-client", "test-secret", "test-user", "test-password", base + "/token", base + "/gre/", HttpClient.newHttpClient());
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void sendsTheSignedZipWithItsHashAndReturnsOnlyATicket() throws Exception {
        response.set("{\"numTicket\":\"ticket-1\"}"); var result = client.send("20123456789-09-T001-00000001", "<Signed/>");
        assertTrue(result.success()); assertEquals("ticket-1", result.ticket());
        var file = mapper.readTree(body.get()).path("archivo");
        byte[] zip = Base64.getDecoder().decode(file.path("arcGreZip").asText());
        assertEquals("<Signed/>", ZipUtil.unzipFirst(zip));
        assertTrue(file.path("nomArchivo").asText().endsWith(".zip"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip)), file.path("hashZip").asText());
    }
    @Test void neverClaimsAcceptanceWithoutACdr() {
        response.set("{\"codRespuesta\":\"0\"}"); var result = client.checkStatus("ticket");
        assertFalse(result.accepted()); assertFalse(result.rejected()); assertEquals("MISSING_CDR", result.code());
        response.set("{\"codRespuesta\":\"98\"}"); assertEquals("98", client.checkStatus("ticket").code());
    }
    @Test void preservesTheActualCdrRejectionAndObservation() throws Exception {
        response.set(cdrResponse("2335", "Descripción no válida", false));
        var rejected = client.checkStatus("ticket"); assertTrue(rejected.rejected()); assertFalse(rejected.accepted());
        assertEquals("2335", rejected.code()); assertEquals("Descripción no válida", rejected.message()); assertNotNull(rejected.cdrXml());
        response.set(cdrResponse("0", "Aceptada", true));
        var accepted = client.checkStatus("ticket"); assertTrue(accepted.accepted()); assertTrue(accepted.observed()); assertFalse(accepted.rejected());
    }
    @Test void transportAndAuthenticationFailuresAreNeverFiscalRejections() {
        status.set(503); var state = client.checkStatus("ticket"); assertFalse(state.rejected()); assertEquals("HTTP_503", state.code());
        var uncertain = client.send("guide", "<Signed/>"); assertTrue(uncertain.uncertain()); assertFalse(uncertain.retryable());
        assertFalse(uncertain.error().contains("test-secret"));
    }
    @Test void failureBeforeSubmissionIsSafeToRetry() {
        tokenStatus.set(401); var result = client.send("guide", "<Signed/>");
        assertFalse(result.success()); assertTrue(result.retryable()); assertFalse(result.uncertain()); assertEquals(0, submissions.get());
    }
    @Test void malformedCdrCannotAuthorizeTransportOrReadExternalFiles() throws Exception {
        String xml = "<!DOCTYPE x [<!ENTITY ext SYSTEM 'file:///etc/hostname'>]><x><ResponseCode>0</ResponseCode><Description>&ext;</Description></x>";
        response.set(mapper.writeValueAsString(Map.of("codRespuesta", "0", "arcCdr", Base64.getEncoder().encodeToString(ZipUtil.zipContent("R.xml", xml.getBytes(StandardCharsets.UTF_8))))));
        var state = client.checkStatus("ticket"); assertFalse(state.accepted()); assertFalse(state.rejected());
    }
    private String cdrResponse(String code, String description, boolean note) throws Exception {
        String xml = "<ApplicationResponse xmlns:cbc='urn:example'><cbc:ResponseCode>" + code + "</cbc:ResponseCode><cbc:Description>" + description + "</cbc:Description>" + (note ? "<cbc:Note>Observación</cbc:Note>" : "") + "</ApplicationResponse>";
        return mapper.writeValueAsString(Map.of("codRespuesta", code.equals("0") ? "0" : "99", "arcCdr", Base64.getEncoder().encodeToString(ZipUtil.zipContent("R.xml", xml.getBytes(StandardCharsets.UTF_8)))));
    }
}
