package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.app.security.SecretResolver;
import io.carabo.storage.DocumentStorage;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class CustomerDocumentMail {
    private static final Logger LOG = Logger.getLogger(CustomerDocumentMail.class);
    @Inject CustomerMailOutbox outbox;
    @Inject CustomerDocumentPdf pdfs;
    @Inject ObjectMapper json;
    @Inject SecretResolver secrets;
    @ConfigProperty(name = "carabo.mailer.url", defaultValue = "http://mailer:43622") String mailerUrl;
    @ConfigProperty(name = "carabo.mailer.token") Optional<String> mailerToken;
    @ConfigProperty(name = "carabo.storage.base-path", defaultValue = "/app/storage/documents") String storagePath;
    @ConfigProperty(name = "carabo.storage.encryption-key") Optional<String> storageKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    @Scheduled(every = "5s", identity = "customer-document-mail", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void deliverDue() {
        if (mailerToken.isEmpty()) return;
        for (int count = 0; count < 5; count++) {
            CustomerMailOutbox.Claim claim = outbox.claim();
            if (claim == null) return;
            publish(claim);
        }
    }

    void publish(CustomerMailOutbox.Claim claim) {
        try {
            String request = claim.payload();
            if ("{}".equals(request)) {
                CustomerMailOutbox.MailDocument document = outbox.document(claim);
                String key = storageKey.filter(value -> !value.isBlank()).map(secrets::resolve).orElse(null);
                DocumentStorage storage = new DocumentStorage(storagePath, key);
                String xml = storage.getSignedXml(claim.business(), document.series(), document.number()).orElseThrow(() -> new IllegalStateException("MAIL_XML_PENDING"));
                String cdr = storage.getCdr(claim.business(), document.series(), document.number()).orElseThrow(() -> new IllegalStateException("MAIL_CDR_PENDING"));
                byte[] pdf = pdfs.ready(claim, document, storage);
                request = json.writeValueAsString(prepare(claim.recipient(), document, xml, cdr, pdf));
                if (!outbox.prepared(claim, request)) return;
            }
            String operation = operationId(claim);
            String body = "{\"businessCode\":" + json.writeValueAsString(claim.business()) + ",\"operationId\":" + json.writeValueAsString(operation) + ",\"request\":" + request + "}";
            String token = secrets.resolve(mailerToken.orElseThrow());
            if (token.length() < 32) throw new IllegalStateException("MAIL_SERVICE_NOT_CONFIGURED");
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(mailerUrl.replaceAll("/$", "") + "/mailer/operations"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 202 || !operation.equals(json.readTree(response.body()).path("operationId").asText()))
                throw new IllegalStateException("MAIL_PUBLICATION_REJECTED");
            outbox.finish(claim, true);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            outbox.finish(claim, false);
        } catch (Exception failure) {
            String code = java.util.Set.of("MAIL_PDF_PENDING", "MAIL_PDF_NOT_CONFIGURED", "MAIL_XML_PENDING", "MAIL_CDR_PENDING", "MAIL_DOCUMENT_NOT_ACCEPTED")
                .contains(failure.getMessage() == null ? "" : failure.getMessage()) ? failure.getMessage() : "MAIL_PUBLICATION_FAILED";
            LOG.warnf("Customer document mail %s requires retry (%s)", claim.documentId(), code);
            outbox.finish(claim, false, code);
        }
    }

    static String operationId(CustomerMailOutbox.Claim claim) { return "carabo/document/" + claim.documentId() + "/customer"; }

    static Map<String, Object> prepare(String recipient, CustomerMailOutbox.MailDocument document, String xml, String cdr, byte[] pdf) {
        CustomerDocumentPdf.requirePdf(pdf);
        String base = document.issuerRuc() + "-" + document.docType() + "-" + document.fullNumber();
        List<Map<String, String>> attachments = new ArrayList<>();
        attachments.add(attachment(base + ".pdf", "application/pdf", pdf));
        attachments.add(attachment(base + ".xml", "application/xml", xml.getBytes(StandardCharsets.UTF_8)));
        attachments.add(attachment("R-" + base + ".xml", "application/xml", cdr.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("to", recipient);
        request.put("subject", (document.test() ? "[PRUEBA BETA] " : "") + "Comprobante " + document.fullNumber());
        request.put("text", (document.test() ? "COMPROBANTE DE PRUEBA BETA.\n\n" : "") + document.issuerName() + "\nComprobante: " + document.fullNumber() + "\nTotal: " + document.currency() + " " + document.total() + "\n\nAdjuntamos la representación PDF, el XML firmado del comprobante y la constancia de recepción (CDR) de SUNAT.");
        request.put("attachments", attachments);
        return request;
    }
    private static Map<String, String> attachment(String filename, String contentType, byte[] content) {
        return Map.of("filename", filename, "contentType", contentType, "content", Base64.getEncoder().encodeToString(content));
    }
}
