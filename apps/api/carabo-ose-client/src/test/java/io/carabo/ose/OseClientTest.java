package io.carabo.ose;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OseClientTest {
    private HttpServer server;
    private final AtomicReference<Response> next = new AtomicReference<>();
    private final AtomicReference<HttpExchangeSnapshot> request = new AtomicReference<>();
    private OseClient client;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/documents", this::respond);
        server.start();
        client = new OseClient("http://localhost:" + server.getAddress().getPort(), "token-value");
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    @Test
    void sendsUtf8JsonWithAuthorizationAndIdempotency() throws Exception {
        String cdr = "<ApplicationResponse>aceptado</ApplicationResponse>";
        next.set(new Response(201, """
            {"status":"ACCEPTED","responseCode":"0","responseMessage":"Aceptado","digestValue":"abc",
             "externalId":"ose-42","cdrContent":"%s"}
            """.formatted(Base64.getEncoder().encodeToString(cdr.getBytes(StandardCharsets.UTF_8))), "application/json"));

        var result = client.send("20123456789-01-F001-42", "<Invoice><Name>José</Name></Invoice>");

        assertTrue(result.accepted());
        assertEquals(cdr, result.cdrXml());
        assertEquals("ose-42", result.externalId());
        assertEquals("Bearer token-value", request.get().authorization());
        assertEquals("20123456789-01-F001-42", request.get().idempotencyKey());
        JsonNode payload = new ObjectMapper().readTree(request.get().body());
        assertEquals("20123456789-01-F001-42", payload.get("fileName").asText());
    }

    @Test
    void keepsObservedAndPendingOutcomesDistinct() {
        next.set(new Response(200, "{\"status\":\"OBSERVED\",\"responseCode\":\"0\"}", "application/json"));
        var observed = client.send("invoice", "<Invoice/>");
        assertTrue(observed.accepted());
        assertTrue(observed.observed());

        next.set(new Response(202, "{\"status\":\"PROCESSING\",\"externalId\":\"job-1\"}", "application/json"));
        var processing = client.send("invoice", "<Invoice/>");
        assertTrue(processing.processing());
        assertFalse(processing.accepted());
    }

    @Test
    void classifiesRateLimitsAndServerErrorsAsRetryable() {
        next.set(new Response(429, "{\"message\":\"rate limited\"}", "application/json"));
        var rateLimit = client.send("invoice", "<Invoice/>");
        assertTrue(rateLimit.retryable());
        assertEquals("HTTP_429", rateLimit.responseCode());

        next.set(new Response(503, "maintenance", "text/plain"));
        assertTrue(client.send("invoice", "<Invoice/>").retryable());
    }

    @Test
    void downloadsXmlCdrAndEncodesExternalIdPath() {
        next.set(new Response(200, "<ApplicationResponse/>", "application/xml"));

        var result = client.downloadCdr("job id/42");

        assertTrue(result.success());
        assertEquals("<ApplicationResponse/>", result.cdrXml());
        assertTrue(request.get().path().endsWith("job%20id%2F42/cdr"));
    }

    private void respond(HttpExchange exchange) {
        try (exchange) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            request.set(new HttpExchangeSnapshot(exchange.getRequestURI().toString(), body,
                exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestHeaders().getFirst("Idempotency-Key")));
            Response response = next.get();
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record Response(int status, String body, String contentType) {}
    private record HttpExchangeSnapshot(String path, String body, String authorization, String idempotencyKey) {}
}
