package io.carabo.app.service;

import com.sun.net.httpserver.HttpServer;
import io.carabo.app.security.SecretResolver;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.SendMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentServiceRetryTest {
    @Test
    void resolvesAnExistingTicketWithoutSubmittingOrSigningTheDocumentAgain() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                byte[] response = "{\"status\":\"ACCEPTED\",\"responseCode\":\"0\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            var tenant = new Tenant();
            tenant.setBusinessCode("B20123456789");
            tenant.setSendMode(SendMode.OSE);
            tenant.setOseBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            tenant.setOseApiToken("test-token");
            var doc = new Document();
            doc.setBusinessCode(tenant.getBusinessCode());
            doc.setStatus(DocumentStatus.ERROR);
            doc.setSunatTicket("existing-ticket");
            doc.setSendMode(SendMode.OSE);
            var id = UUID.randomUUID();

            var service = new DocumentService();
            service.em = (EntityManager) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{EntityManager.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "find" -> {
                        assertEquals(List.of(Document.class, id, LockModeType.PESSIMISTIC_WRITE), List.of(args));
                        yield doc;
                    }
                    case "persist" -> null;
                    case "merge" -> args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            service.tenantService = new TenantService() {
                @Override public Tenant resolve(String businessCode) { return tenant; }
            };
            service.fiscalRouting = new FiscalRoutingService();
            service.secretResolver = new SecretResolver() {
                @Override public String resolve(String value) { return value; }
            };
            service.notificationService = new NotificationService() {
                @Override public void enqueueFor(Document document, Tenant current) {}
            };

            service.retry(id);
            service.retry(id);

            assertEquals(List.of("GET /api/v1/documents/existing-ticket/status"), requests);
            assertEquals(DocumentStatus.ACCEPTED, doc.getStatus());
            assertEquals("existing-ticket", doc.getSunatTicket());
        } finally {
            server.stop(0);
        }
    }
}
