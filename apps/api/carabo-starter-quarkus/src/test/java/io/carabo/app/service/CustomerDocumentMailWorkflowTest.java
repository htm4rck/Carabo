package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.carabo.app.client.LayoutClient;
import io.carabo.app.security.SecretResolver;
import io.carabo.core.entity.Document;
import io.carabo.core.enums.DocumentType;
import io.carabo.storage.DocumentStorage;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDocumentMailWorkflowTest {
    @TempDir Path directory;
    static final byte[] PDF = "%PDF-customer-document".getBytes(StandardCharsets.US_ASCII);
    static final String BUSINESS = "B20123456789";
    final ObjectMapper json = new ObjectMapper();

    static class Queue extends CustomerMailOutbox {
        String frozen, failure;
        boolean lease = true, queued, storedBeforePrepared;
        DocumentStorage storage;
        @Override public MailDocument document(Claim claim) { return new MailDocument("B001", 5, "B001-00000005", "20123456789", "Comercio", "03", "152.00", "PEN", true); }
        @Override public Document pdfDocument(Claim claim) { return new Document(); }
        @Override public boolean prepared(Claim claim, String request) {
            storedBeforePrepared = storage.getPdf(BUSINESS, "B001", 5).isPresent();
            if (!lease) return false;
            frozen = request; return true;
        }
        @Override public void finish(Claim claim, boolean accepted) { queued = accepted; }
        @Override public void finish(Claim claim, boolean accepted, String code) { queued = accepted; failure = code; }
    }

    CustomerDocumentMail worker(Queue queue, LayoutClient layout, String url) {
        queue.storage = new DocumentStorage(directory.toString(), "0123456789abcdef".repeat(4));
        queue.storage.storeSignedXml(BUSINESS, "B001", 5, "<Invoice>original</Invoice>");
        queue.storage.storeCdr(BUSINESS, "B001", 5, "<ApplicationResponse>original</ApplicationResponse>");
        var pdf = new CustomerDocumentPdf(); pdf.outbox = queue; pdf.layout = layout; pdf.secrets = new SecretResolver();
        pdf.serviceToken = Optional.of("layout-customer-fixture-token-1234567890");
        var worker = new CustomerDocumentMail(); worker.outbox = queue; worker.pdfs = pdf; worker.json = json;
        worker.secrets = new SecretResolver(); worker.storagePath = directory.toString(); worker.storageKey = Optional.of("0123456789abcdef".repeat(4));
        worker.mailerUrl = url; worker.mailerToken = Optional.of("mailer-customer-fixture-token-1234567890");
        return worker;
    }

    CustomerMailOutbox.Claim claim(UUID document, String payload, int attempt) {
        return new CustomerMailOutbox.Claim(UUID.randomUUID(), UUID.randomUUID(), BUSINESS, document, "guest@example.invalid", payload, attempt);
    }

    @Test void waitsForPdfThenStoresItAndRetriesTheIdenticalPreparedMail() throws Exception {
        var renders = new AtomicInteger(); var posts = new AtomicInteger(); var bodies = new ArrayList<String>();
        var queue = new Queue(); UUID document = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mailer/operations", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); bodies.add(body);
            byte[] response = json.writeValueAsBytes(java.util.Map.of("operationId", json.readTree(body).path("operationId").asText()));
            exchange.sendResponseHeaders(posts.incrementAndGet() == 1 ? 503 : 202, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            LayoutClient layout = new LayoutClient() { @Override public byte[] generateDocumentPdf(Document value, String token) { return renders.incrementAndGet() == 1 ? null : PDF; } };
            var worker = worker(queue, layout, "http://127.0.0.1:" + server.getAddress().getPort());
            worker.publish(claim(document, "{}", 1));
            assertEquals("MAIL_PDF_PENDING", queue.failure); assertNull(queue.frozen); assertEquals(0, posts.get());
            worker.publish(claim(document, "{}", 2));
            assertTrue(queue.storedBeforePrepared); assertFalse(queue.queued); assertEquals(1, posts.get());
            assertArrayEquals(PDF, queue.storage.getPdf(BUSINESS, "B001", 5).orElseThrow());
            assertEquals("<Invoice>original</Invoice>", queue.storage.getSignedXml(BUSINESS, "B001", 5).orElseThrow());
            assertEquals("<ApplicationResponse>original</ApplicationResponse>", queue.storage.getCdr(BUSINESS, "B001", 5).orElseThrow());
            var attachments = json.readTree(queue.frozen).path("attachments");
            assertEquals(3, attachments.size()); assertEquals("application/pdf", attachments.get(0).path("contentType").asText());
            assertNotEquals(attachments.get(1).path("content").asText(), attachments.get(2).path("content").asText());
            worker.publish(claim(document, queue.frozen, 3));
            assertTrue(queue.queued); assertEquals(2, posts.get()); assertEquals(2, renders.get());
            assertEquals(bodies.get(0), bodies.get(1));
        } finally { server.stop(0); }
    }

    @Test void reusesAStoredPdfAndDoesNotPublishAfterLosingTheLease() {
        var queue = new Queue();
        var worker = worker(queue, new LayoutClient() { @Override public byte[] generateDocumentPdf(Document document, String token) { fail("Stored PDF must be reused"); return null; } }, "http://127.0.0.1:1");
        queue.storage.storePdf(BUSINESS, "B001", 5, PDF); queue.lease = false;
        worker.publish(claim(UUID.randomUUID(), "{}", 1));
        assertNull(queue.frozen); assertFalse(queue.queued); assertNull(queue.failure); assertTrue(queue.storedBeforePrepared);
    }

    @Test void downloadsUseTheServiceIdentityAndKeepTheCanonicalMailRepresentation() {
        var queue = new Queue(); var calls = new AtomicInteger();
        var worker = worker(queue, new LayoutClient() {
            @Override public byte[] generateDocumentPdf(Document document, String format, String token) {
                calls.incrementAndGet();
                assertEquals("layout-customer-fixture-token-1234567890", token);
                return format == null ? PDF : "%PDF-alternative-A4".getBytes(StandardCharsets.US_ASCII);
            }
        }, "http://127.0.0.1:1");
        worker.pdfs.storagePath = directory.toString(); worker.pdfs.storageKey = worker.storageKey;
        Document document = new Document(); document.setBusinessCode(BUSINESS);
        document.setSeries("B001"); document.setNumber(5); document.setDocType(DocumentType.RECEIPT);
        assertArrayEquals(PDF, worker.pdfs.download(document, null, null));
        assertArrayEquals(PDF, worker.pdfs.download(document, null, null));
        assertEquals(1, calls.get());
        assertNotEquals(new String(PDF, StandardCharsets.US_ASCII), new String(worker.pdfs.download(document, "A4", null), StandardCharsets.US_ASCII));
        assertArrayEquals(PDF, queue.storage.getPdf(BUSINESS, "B001", 5).orElseThrow());
    }
}
