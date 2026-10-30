package io.carabo.app.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.carabo.core.entity.Document;
import io.carabo.core.enums.DocumentType;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LayoutClientTest {
    @Test
    void acceptsACreatedPdfAndForwardsTheAuthenticatedSessionAndDocumentTenant() throws Exception {
        var authorization = new AtomicReference<String>();
        var business = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/layout/pdf/generate", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            business.set(exchange.getRequestHeaders().getFirst("business-code"));
            exchange.getRequestBody().readAllBytes();
            byte[] pdf = "%PDF-test".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, pdf.length);
            exchange.getResponseBody().write(pdf);
            exchange.close();
        });
        server.start();
        try {
            LayoutClient client = new LayoutClient();
            client.layoutBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/layout";
            client.objectMapper = new ObjectMapper();
            Document document = new Document();
            document.setBusinessCode("B20123456789");
            document.setDocType(DocumentType.RECEIPT);
            document.setSeries("B001");
            document.setNumber(1);
            assertNotNull(client.generatePdf(document, "A4", "Bearer administrative-session"));
            assertEquals("Bearer administrative-session", authorization.get());
            assertEquals("B20123456789", business.get());
        } finally { server.stop(0); }
    }
}
