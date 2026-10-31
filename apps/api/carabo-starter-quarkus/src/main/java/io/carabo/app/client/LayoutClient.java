package io.carabo.app.client;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;
import io.carabo.core.entity.gre.DispatchGuide;
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
        return generatePdf(doc, pageFormat, null);
    }

    public byte[] generatePdf(Document doc, String pageFormat, String authorization) {
        return generatePdf(resolveTemplateType(doc),
            pageFormat != null ? pageFormat : resolveDefaultFormat(doc), buildTemplateData(doc), doc.getBusinessCode(), authorization);
    }

    public byte[] generatePdf(DispatchGuide guide, String pageFormat) { return generatePdf(guide,pageFormat,null); }

    public byte[] generatePdf(DispatchGuide guide, String pageFormat, String authorization) {
        Map<String, Object> data = new HashMap<>();
        data.put("fullNumber", guide.getFullNumber());
        data.put("docType", "09");
        data.put("status", guide.getStatus().name());
        data.put("environment", guide.getEnvironment());
        data.put("sunatResponseCode", guide.getSunatResponseCode());
        data.put("docTypeName", "Guía de remisión electrónica");
        data.put("issueDate", guide.getIssueDate() != null ? guide.getIssueDate().toString() : "");
        data.put("startDate", guide.getStartDate() != null ? guide.getStartDate().toString() : "");
        data.put("issuer", Map.of("ruc", safe(guide.getIssuerRuc()), "name", safe(guide.getIssuerName())));
        data.put("receiver", Map.of("docType", safe(guide.getReceiverDocType()),
            "doc", safe(guide.getReceiverDoc()), "name", safe(guide.getReceiverName())));
        data.put("origin", Map.of("ubigeo", safe(guide.getOriginUbigeo()), "address", safe(guide.getOriginAddress())));
        data.put("destination", Map.of("ubigeo", safe(guide.getDestinationUbigeo()), "address", safe(guide.getDestinationAddress())));
        data.put("transportMode", guide.getTransportMode());
        data.put("grossWeight", guide.getGrossWeight());
        data.put("weightUnit", guide.getWeightUnit());
        data.put("packageCount", guide.getPackageCount());
        data.put("transferReasonCode", guide.getTransferReasonCode());
        data.put("transferReason", guide.getTransferReasonDesc());
        data.put("carrier", Map.of("ruc", safe(guide.getCarrierRuc()), "name", safe(guide.getCarrierName())));
        data.put("driver", Map.of("docType", safe(guide.getDriverDocType()), "doc", safe(guide.getDriverDoc()),
            "name", safe(guide.getDriverName()), "license", safe(guide.getDriverLicense())));
        data.put("vehiclePlate", guide.getVehiclePlate());
        data.put("hashCode", guide.getHashCode());
        data.put("items", guide.getItems().stream().map(item -> Map.<String, Object>of(
            "lineNumber", item.getLineNumber(), "productCode", safe(item.getProductCode()),
            "productName", safe(item.getProductName()), "unitCode", safe(item.getUnitCode()),
            "quantity", item.getQuantity(), "weight", item.getWeight() != null ? item.getWeight() : ""
        )).toList());
        return generatePdf("ELECTRONIC_DISPATCH_GUIDE", pageFormat != null ? pageFormat : "A4", data, guide.getBusinessCode(), authorization);
    }

    private byte[] generatePdf(String templateType, String pageFormat, Map<String, Object> data) {
        return generatePdf(templateType, pageFormat, data, null, null);
    }

    private byte[] generatePdf(String templateType, String pageFormat, Map<String, Object> data, String businessCode, String authorization) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("templateType", templateType);
            payload.put("pageFormat", pageFormat);
            payload.put("data", data);

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(layoutBaseUrl + "/pdf/generate"))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(25));
            if (businessCode != null) builder.header("business-code", businessCode);
            if (authorization != null && authorization.startsWith("Bearer ")) builder.header("Authorization", authorization);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(json)).build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body().length >= 5 &&
                new String(response.body(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
                return response.body();
            }

            LOG.errorf("Layout PDF generation failed: status=%d contentType=%s",
                response.statusCode(), response.headers().firstValue("content-type").orElse("unknown"));
            return null;
        } catch (Exception e) {
            LOG.error("Failed to call layout service for PDF generation", e);
            return null;
        }
    }

    private String safe(String value) { return value == null ? "" : value; }

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
        data.put("qrDataUrl", qrDataUrl(qr));
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

    private String qrDataUrl(String content) {
        try {
            var matrix = new com.google.zxing.qrcode.QRCodeWriter().encode(content,
                com.google.zxing.BarcodeFormat.QR_CODE, 180, 180);
            StringBuilder svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 180 180\"><rect width=\"180\" height=\"180\" fill=\"white\"/><path d=\"");
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x,y)) svg.append("M").append(x).append(" ").append(y).append("h1v1h-1z");
                }
            }
            svg.append("\" fill=\"black\"/></svg>");
            return "data:image/svg+xml;base64," + java.util.Base64.getEncoder().encodeToString(
                svg.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (com.google.zxing.WriterException error) {
            throw new IllegalArgumentException("No se pudo generar el QR del comprobante", error);
        }
    }
}
