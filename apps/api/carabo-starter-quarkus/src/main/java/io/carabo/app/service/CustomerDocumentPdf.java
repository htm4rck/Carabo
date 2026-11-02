package io.carabo.app.service;

import io.carabo.app.client.LayoutClient;
import io.carabo.app.security.SecretResolver;
import io.carabo.storage.DocumentStorage;
import io.carabo.core.entity.Document;
import io.carabo.core.enums.DocumentType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Prepares the stored representation before the customer mail is frozen. */
@ApplicationScoped
public class CustomerDocumentPdf {
    @Inject CustomerMailOutbox outbox;
    @Inject LayoutClient layout;
    @Inject SecretResolver secrets;
    @ConfigProperty(name = "carabo.layout.service-token") Optional<String> serviceToken;
    @ConfigProperty(name = "carabo.storage.base-path", defaultValue = "/app/storage/documents") String storagePath;
    @ConfigProperty(name = "carabo.storage.encryption-key") Optional<String> storageKey;

    public byte[] ready(CustomerMailOutbox.Claim claim, CustomerMailOutbox.MailDocument document, DocumentStorage storage) {
        Optional<byte[]> existing = storage.getPdf(claim.business(), document.series(), document.number());
        if (existing.isPresent()) return requirePdf(existing.get());
        byte[] pdf = requirePdf(layout.generateDocumentPdf(outbox.pdfDocument(claim), token()));
        storage.storePdf(claim.business(), document.series(), document.number(), pdf);
        return requirePdf(storage.getPdf(claim.business(), document.series(), document.number())
            .orElseThrow(() -> new IllegalStateException("MAIL_PDF_PENDING")));
    }

    public byte[] download(Document document, String format, String authorization) {
        DocumentStorage storage = new DocumentStorage(storagePath, storageKey.filter(value -> !value.isBlank()).map(secrets::resolve).orElse(null));
        boolean canonical = format == null || format.equals(document.getDocType() == DocumentType.RECEIPT ? "TICKET_80MM" : "A4");
        if (canonical) {
            Optional<byte[]> existing = storage.getPdf(document.getBusinessCode(), document.getSeries(), document.getNumber());
            if (existing.isPresent()) return requirePdf(existing.get());
        }
        byte[] pdf = requirePdf(authorization != null && authorization.startsWith("Bearer ")
            ? layout.generatePdf(document, format, authorization)
            : layout.generateDocumentPdf(document, format, token()));
        if (canonical) storage.storePdf(document.getBusinessCode(), document.getSeries(), document.getNumber(), pdf);
        return pdf;
    }

    private String token() {
        String value = serviceToken.filter(token -> !token.isBlank()).map(secrets::resolve)
            .orElseThrow(() -> new IllegalStateException("MAIL_PDF_NOT_CONFIGURED"));
        if (value.length() < 32 || value.length() > 512) throw new IllegalStateException("MAIL_PDF_NOT_CONFIGURED");
        return value;
    }

    static byte[] requirePdf(byte[] pdf) {
        if (pdf == null || pdf.length < 5 || !new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
            throw new IllegalStateException("MAIL_PDF_PENDING");
        return pdf;
    }
}
