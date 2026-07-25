package io.carabo.app.client;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;

@ApplicationScoped
public class LayoutClient {

    private static final Logger LOG = Logger.getLogger(LayoutClient.class);

    @ConfigProperty(name = "carabo.layout.base-url", defaultValue = "http://localhost:47617/layout")
    String layoutBaseUrl;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Calls tordo.layout to generate PDF for an electronic document.
     * templateType mapping: INVOICE→ELECTRONIC_INVOICE, RECEIPT→ELECTRONIC_RECEIPT, etc.
     * pageFormat: A4 for invoices/NC/ND, TICKET_80MM for boletas
     */
    public byte[] generatePdf(Document doc, String pageFormat) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("templateType", resolveTemplateType(doc));
            payload.put("pageFormat", pageFormat != null ? pageFormat : resolveDefaultFormat(doc));
            payload.put("data", buildTemplateData(doc));

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(layoutBaseUrl + "/pdf/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return response.body();
            }

            LOG.errorf("Layout PDF generation failed: status=%d body=%s",
                response.statusCode(), new String(response.body()));
            return null;
        } catch (Exception e) {
            LOG.error("Failed to call layout service for PDF generation", e);
            return null;
        }
    }

    private String resolveTemplateType(Document doc) {
        return switch (doc.getDocType()) {
            case INVOICE -> "ELECTRONIC_INVOICE";
            case RECEIPT -> "ELECTRONIC_RECEIPT";
            case CREDIT_NOTE -> "ELECTRONIC_CREDIT_NOTE";
            case DEBIT_NOTE -> "ELECTRONIC_DEBIT_NOTE";
            case DISPATCH_GUIDE -> "ELECTRONIC_DISPATCH_GUIDE";
            default -> "ELECTRONIC_INVOICE";
        };
    }

    private String resolveDefaultFormat(Document doc) {
        return switch (doc.getDocType()) {
            case RECEIPT -> "TICKET_80MM";
            default -> "A4";
        };
    }

    private Map<String, Object> buildTemplateData(Document doc) {
        Map<String, Object> data = new HashMap<>();

        // Header
        data.put("fullNumber", doc.getFullNumber());
        data.put("docType", doc.getDocType().sunatCode());
        data.put("docTypeName", doc.getDocType().description());
        data.put("issueDate", doc.getIssueDate() != null ? doc.getIssueDate().toString() : "");
        data.put("dueDate", doc.getDueDate() != null ? doc.getDueDate().toString() : "");
        data.put("currency", doc.getCurrency());

        // Issuer
        Map<String, String> issuer = new HashMap<>();
        issuer.put("ruc", doc.getIssuerRuc());
        issuer.put("name", doc.getIssuerName());
        issuer.put("tradeName", doc.getIssuerTrade());
        issuer.put("address", doc.getIssuerAddress());
        issuer.put("ubigeo", doc.getIssuerUbigeo());
        data.put("issuer", issuer);

        // Receiver
        Map<String, String> receiver = new HashMap<>();
        receiver.put("docType", doc.getReceiverDocType());
        receiver.put("doc", doc.getReceiverDoc());
        receiver.put("name", doc.getReceiverName());
        receiver.put("address", doc.getReceiverAddress());
        data.put("receiver", receiver);

        // Lines
        List<Map<String, Object>> lines = doc.getLines().stream().map(line -> {
            Map<String, Object> l = new HashMap<>();
            l.put("lineNumber", line.getLineNumber());
            l.put("productCode", line.getProductCode());
            l.put("productName", line.getProductName());
            l.put("unitCode", line.getUnitCode());
            l.put("quantity", line.getQuantity());
            l.put("unitPrice", line.getUnitPrice());
            l.put("discount", line.getDiscount());
            l.put("taxBase", line.getTaxBase());
            l.put("igvAmount", line.getIgvAmount());
            l.put("lineTotal", line.getLineTotal());
            return l;
        }).toList();
        data.put("lines", lines);

        // Totals
        data.put("taxBase", doc.getTaxBase());
        data.put("igvAmount", doc.getIgvAmount());
        data.put("total", doc.getTotal());
        data.put("discountTotal", doc.getDiscountTotal());

        // QR (SUNAT format: RUC|TIPO|SERIE|NUMERO|IGV|TOTAL|FECHA|TIPODOC_RECEPTOR|DOC_RECEPTOR|HASH)
        String qr = String.join("|",
            doc.getIssuerRuc(),
            doc.getDocType().sunatCode(),
            doc.getSeries(),
            String.valueOf(doc.getNumber()),
            doc.getIgvAmount().toPlainString(),
            doc.getTotal().toPlainString(),
            doc.getIssueDate() != null ? doc.getIssueDate().toString() : "",
            doc.getReceiverDocType(),
            doc.getReceiverDoc(),
            doc.getHashCode() != null ? doc.getHashCode() : ""
        );
        data.put("qrContent", qr);
        data.put("hashCode", doc.getHashCode());

        // Reference (NC/ND)
        if (doc.getRefSeries() != null) {
            Map<String, Object> ref = new HashMap<>();
            ref.put("docType", doc.getRefDocType());
            ref.put("fullNumber", doc.getRefSeries() + "-" + String.format("%08d", doc.getRefNumber()));
            ref.put("reason", doc.getRefReason());
            data.put("reference", ref);
        }

        return data;
    }
}
