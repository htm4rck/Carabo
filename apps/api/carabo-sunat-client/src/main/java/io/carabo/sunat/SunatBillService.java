package io.carabo.sunat;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * SOAP client for SUNAT BillService.
 * Endpoints:
 *   - Beta: https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService
 *   - Production: https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService
 *
 * Operations: sendBill, getStatus, sendSummary
 */
public class SunatBillService {

    private static final String BETA_URL = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    private static final String PROD_URL = "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService";

    private final String endpoint;
    private final String solUser;    // RUC + usuario SOL (e.g. 20123456789MODDATOS)
    private final String solPassword;
    private final HttpClient httpClient;

    public SunatBillService(String endpoint, String solUser, String solPassword) {
        this.endpoint = endpoint;
        this.solUser = solUser;
        this.solPassword = solPassword;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public static SunatBillService beta(String solUser, String solPassword) {
        return new SunatBillService(BETA_URL, solUser, solPassword);
    }

    public static SunatBillService production(String solUser, String solPassword) {
        return new SunatBillService(PROD_URL, solUser, solPassword);
    }

    /**
     * Sends a signed XML to SUNAT (Invoice/CreditNote/DebitNote).
     * Returns the CDR (Constancia de Recepción) XML content.
     */
    public SendBillResult sendBill(String fileName, String signedXml) {
        try {
            byte[] zipBytes = zipContent(fileName + ".xml", signedXml.getBytes(StandardCharsets.UTF_8));
            String base64Zip = Base64.getEncoder().encodeToString(zipBytes);

            String soapBody = buildSendBillSoap(fileName + ".zip", base64Zip);
            String response = callSoap(soapBody, "sendBill");

            // Parse response
            String applicationResponse = extractTag(response, "applicationResponse");
            if (applicationResponse != null) {
                byte[] cdrZip = Base64.getDecoder().decode(applicationResponse);
                String cdrXml = unzipFirst(cdrZip);
                String responseCode = extractTag(cdrXml, "cbc:ResponseCode");
                String description = extractTag(cdrXml, "cbc:Description");
                boolean accepted = "0".equals(responseCode);
                return new SendBillResult(accepted, responseCode, description, cdrXml, null);
            }

            // Check for fault
            String faultCode = extractTag(response, "faultcode");
            String faultString = extractTag(response, "faultstring");
            return new SendBillResult(false, faultCode, faultString, null, null);

        } catch (Exception e) {
            return new SendBillResult(false, "ERROR", e.getMessage(), null, null);
        }
    }

    /**
     * Sends a summary (Resumen Diario / Comunicación de Baja).
     * Returns a ticket number for later status check.
     */
    public String sendSummary(String fileName, String signedXml) {
        try {
            byte[] zipBytes = zipContent(fileName + ".xml", signedXml.getBytes(StandardCharsets.UTF_8));
            String base64Zip = Base64.getEncoder().encodeToString(zipBytes);

            String soapBody = buildSendSummarySoap(fileName + ".zip", base64Zip);
            String response = callSoap(soapBody, "sendSummary");

            return extractTag(response, "ticket");
        } catch (Exception e) {
            throw new RuntimeException("sendSummary failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the status of a previously sent summary by ticket.
     */
    public SendBillResult getStatus(String ticket) {
        try {
            String soapBody = buildGetStatusSoap(ticket);
            String response = callSoap(soapBody, "getStatus");

            String statusContent = extractTag(response, "content");
            if (statusContent != null) {
                byte[] cdrZip = Base64.getDecoder().decode(statusContent);
                String cdrXml = unzipFirst(cdrZip);
                String responseCode = extractTag(cdrXml, "cbc:ResponseCode");
                String description = extractTag(cdrXml, "cbc:Description");
                boolean accepted = "0".equals(responseCode);
                return new SendBillResult(accepted, responseCode, description, cdrXml, ticket);
            }

            String statusCode = extractTag(response, "statusCode");
            if ("98".equals(statusCode)) {
                // Still processing
                return new SendBillResult(false, "98", "En proceso", null, ticket);
            }

            return new SendBillResult(false, statusCode, "Unknown status", null, ticket);
        } catch (Exception e) {
            return new SendBillResult(false, "ERROR", e.getMessage(), null, ticket);
        }
    }

    // --- SOAP envelope builders ---

    private String buildSendBillSoap(String fileName, String base64Content) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:ser="http://service.sunat.gob.pe">
              <soapenv:Header>
                <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <wsse:UsernameToken>
                    <wsse:Username>%s</wsse:Username>
                    <wsse:Password>%s</wsse:Password>
                  </wsse:UsernameToken>
                </wsse:Security>
              </soapenv:Header>
              <soapenv:Body>
                <ser:sendBill>
                  <fileName>%s</fileName>
                  <contentFile>%s</contentFile>
                </ser:sendBill>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(solUser, solPassword, fileName, base64Content);
    }

    private String buildSendSummarySoap(String fileName, String base64Content) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:ser="http://service.sunat.gob.pe">
              <soapenv:Header>
                <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <wsse:UsernameToken>
                    <wsse:Username>%s</wsse:Username>
                    <wsse:Password>%s</wsse:Password>
                  </wsse:UsernameToken>
                </wsse:Security>
              </soapenv:Header>
              <soapenv:Body>
                <ser:sendSummary>
                  <fileName>%s</fileName>
                  <contentFile>%s</contentFile>
                </ser:sendSummary>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(solUser, solPassword, fileName, base64Content);
    }

    private String buildGetStatusSoap(String ticket) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:ser="http://service.sunat.gob.pe">
              <soapenv:Header>
                <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <wsse:UsernameToken>
                    <wsse:Username>%s</wsse:Username>
                    <wsse:Password>%s</wsse:Password>
                  </wsse:UsernameToken>
                </wsse:Security>
              </soapenv:Header>
              <soapenv:Body>
                <ser:getStatus>
                  <ticket>%s</ticket>
                </ser:getStatus>
              </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(solUser, solPassword, ticket);
    }

    private String callSoap(String soapBody, String action) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "text/xml; charset=utf-8")
            .header("SOAPAction", "urn:" + action)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(soapBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    // --- Utility ---

    private byte[] zipContent(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String unzipFirst(byte[] zipBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry != null) {
                return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private String extractTag(String xml, String tag) {
        if (xml == null) return null;
        int start = xml.indexOf("<" + tag);
        if (start < 0) return null;
        start = xml.indexOf(">", start) + 1;
        int end = xml.indexOf("</" + tag + ">", start);
        if (end < 0) return null;
        return xml.substring(start, end).trim();
    }

    public record SendBillResult(boolean accepted, String responseCode, String responseMsg, String cdrXml, String ticket) {}
}
