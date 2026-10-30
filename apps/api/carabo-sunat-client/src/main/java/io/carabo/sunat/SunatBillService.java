package io.carabo.sunat;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** SOAP client for SUNAT BillService with explicit fiscal and transport outcomes. */
public class SunatBillService {

    public static final String BETA_URL = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    public static final String PROD_URL = "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final URI endpoint;
    private final String solUser;
    private final String solPassword;
    private final HttpClient httpClient;

    public SunatBillService(String endpoint, String solUser, String solPassword) {
        this(endpoint, solUser, solPassword, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30)).build());
    }

    public SunatBillService(String endpoint, String solUser, String solPassword, HttpClient httpClient) {
        if (endpoint == null || endpoint.isBlank()) throw new IllegalArgumentException("SUNAT endpoint is required");
        if (solUser == null || solUser.isBlank()) throw new IllegalArgumentException("SUNAT SOL user is required");
        if (solPassword == null || solPassword.isBlank()) throw new IllegalArgumentException("SUNAT SOL password is required");
        this.endpoint = URI.create(endpoint);
        this.solUser = solUser;
        this.solPassword = solPassword;
        this.httpClient = httpClient;
    }

    public static SunatBillService beta(String solUser, String solPassword) {
        return new SunatBillService(BETA_URL, solUser, solPassword);
    }

    public static SunatBillService production(String solUser, String solPassword) {
        return new SunatBillService(PROD_URL, solUser, solPassword);
    }

    /** Sends an Invoice/CreditNote/DebitNote ZIP and parses the returned CDR. */
    public SendBillResult sendBill(String fileName, String signedXml) {
        try {
            byte[] zipBytes = zipContent(fileName + ".xml", signedXml.getBytes(StandardCharsets.UTF_8));
            SoapResponse response = callSoap(buildSendBillSoap(fileName + ".zip",
                Base64.getEncoder().encodeToString(zipBytes)), "sendBill");
            return parseBillResponse(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendBillResult.transportError("INTERRUPTED", "SUNAT request interrupted", true);
        } catch (IOException e) {
            return SendBillResult.transportError("TRANSPORT_ERROR", safeMessage(e), true);
        } catch (Exception e) {
            return SendBillResult.transportError("INVALID_RESPONSE", safeMessage(e), false);
        }
    }

    /** Sends an RC/RA batch and returns its asynchronous SUNAT ticket. */
    public String sendSummary(String fileName, String signedXml) {
        try {
            byte[] zipBytes = zipContent(fileName + ".xml", signedXml.getBytes(StandardCharsets.UTF_8));
            SoapResponse response = callSoap(buildSendSummarySoap(fileName + ".zip",
                Base64.getEncoder().encodeToString(zipBytes)), "sendSummary");
            SoapFault fault = parseFault(response.body());
            if (fault != null) throw new SunatClientException(fault.code(), fault.message(), isRetryableFault(fault.code()));
            ensureSuccessfulHttp(response);
            String ticket = firstText(response.body(), "ticket");
            if (ticket == null || ticket.isBlank()) {
                throw new SunatClientException("MISSING_TICKET", "SUNAT response did not include a ticket", true);
            }
            return ticket;
        } catch (SunatClientException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SunatClientException("INTERRUPTED", "SUNAT request interrupted", true, e);
        } catch (Exception e) {
            throw new SunatClientException("TRANSPORT_ERROR", safeMessage(e), true, e);
        }
    }

    /** Gets an RC/RA result; code 98 is represented as processing, not rejected. */
    public SendBillResult getStatus(String ticket) {
        try {
            SoapResponse response = callSoap(buildGetStatusSoap(ticket), "getStatus");
            SoapFault fault = parseFault(response.body());
            if (fault != null) return SendBillResult.transportError(fault.code(), fault.message(), isRetryableFault(fault.code()));
            if (response.statusCode() >= 500) {
                return SendBillResult.transportError("HTTP_" + response.statusCode(), "SUNAT is temporarily unavailable", true);
            }
            if (response.statusCode() >= 400) {
                return SendBillResult.transportError("HTTP_" + response.statusCode(), "SUNAT rejected the HTTP request", false);
            }
            String statusContent = firstText(response.body(), "content");
            if (statusContent != null && !statusContent.isBlank()) {
                return parseCdr(unzipFirst(Base64.getMimeDecoder().decode(statusContent)), ticket);
            }
            String statusCode = firstText(response.body(), "statusCode");
            if ("98".equals(statusCode)) return SendBillResult.processing(ticket);
            return SendBillResult.transportError(statusCode != null ? statusCode : "MISSING_STATUS",
                "SUNAT response did not include a CDR", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendBillResult.transportError("INTERRUPTED", "SUNAT request interrupted", true);
        } catch (Exception e) {
            return SendBillResult.transportError("TRANSPORT_ERROR", safeMessage(e), true);
        }
    }

    private SendBillResult parseBillResponse(SoapResponse response) throws Exception {
        SoapFault fault = parseFault(response.body());
        if (fault != null) return SendBillResult.transportError(fault.code(), fault.message(), isRetryableFault(fault.code()));
        if (response.statusCode() >= 500) {
            return SendBillResult.transportError("HTTP_" + response.statusCode(), "SUNAT is temporarily unavailable", true);
        }
        if (response.statusCode() >= 400) {
            return SendBillResult.transportError("HTTP_" + response.statusCode(), "SUNAT rejected the HTTP request", false);
        }
        String applicationResponse = firstText(response.body(), "applicationResponse");
        if (applicationResponse == null || applicationResponse.isBlank()) {
            return SendBillResult.transportError("MISSING_CDR", "SUNAT response did not include a CDR", true);
        }
        return parseCdr(unzipFirst(Base64.getMimeDecoder().decode(applicationResponse)), null);
    }

    private SendBillResult parseCdr(String cdrXml, String ticket) throws Exception {
        String responseCode = firstText(cdrXml, "ResponseCode");
        String description = firstText(cdrXml, "Description");
        List<String> notes = allText(cdrXml, "Note");
        if (responseCode == null) {
            return SendBillResult.transportError("INVALID_CDR", "CDR did not include ResponseCode", true);
        }
        boolean accepted = "0".equals(responseCode);
        String responseMessage = notes.isEmpty() ? description
            : (description == null ? "" : description + " — ") + String.join("; ", notes);
        return new SendBillResult(accepted, accepted && !notes.isEmpty(), false, false,
            responseCode, responseMessage, cdrXml, ticket, notes);
    }

    private String buildSendBillSoap(String fileName, String base64Content) {
        return envelope("<ser:sendBill><fileName>" + xml(fileName) + "</fileName><contentFile>"
            + base64Content + "</contentFile></ser:sendBill>");
    }

    private String buildSendSummarySoap(String fileName, String base64Content) {
        return envelope("<ser:sendSummary><fileName>" + xml(fileName) + "</fileName><contentFile>"
            + base64Content + "</contentFile></ser:sendSummary>");
    }

    private String buildGetStatusSoap(String ticket) {
        return envelope("<ser:getStatus><ticket>" + xml(ticket) + "</ticket></ser:getStatus>");
    }

    private String envelope(String body) {
        return """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ser="http://service.sunat.gob.pe">
              <soapenv:Header><wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"><wsse:UsernameToken><wsse:Username>%s</wsse:Username><wsse:Password>%s</wsse:Password></wsse:UsernameToken></wsse:Security></soapenv:Header>
              <soapenv:Body>%s</soapenv:Body>
            </soapenv:Envelope>
            """.formatted(xml(solUser), xml(solPassword), body);
    }

    private SoapResponse callSoap(String soapBody, String action) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .header("Content-Type", "text/xml; charset=utf-8")
            .header("SOAPAction", "urn:" + action)
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(soapBody, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new SoapResponse(response.statusCode(), response.body());
    }

    private void ensureSuccessfulHttp(SoapResponse response) {
        if (response.statusCode() >= 400) {
            throw new SunatClientException("HTTP_" + response.statusCode(),
                response.statusCode() >= 500 ? "SUNAT is temporarily unavailable" : "SUNAT rejected the HTTP request",
                response.statusCode() >= 500);
        }
    }

    private static byte[] zipContent(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static String unzipFirst(byte[] zipBytes) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null && entry.isDirectory()) entry = zip.getNextEntry();
            if (entry == null) throw new IOException("CDR ZIP is empty");
            byte[] content = zip.readNBytes(10 * 1024 * 1024 + 1);
            if (content.length > 10 * 1024 * 1024) throw new IOException("CDR ZIP exceeds 10 MB");
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    private static SoapFault parseFault(String xml) {
        if (xml == null || !xml.stripLeading().startsWith("<")) return null;
        try {
            String code = firstText(xml, "faultcode");
            if (code == null) return null;
            return new SoapFault(code, firstText(xml, "faultstring"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstText(String xml, String localName) throws Exception {
        List<String> values = allText(xml, localName);
        return values.isEmpty() ? null : values.getFirst();
    }

    private static List<String> allText(String xml, String localName) throws Exception {
        if (xml == null || xml.isBlank()) return List.of();
        Document document = parseXml(xml);
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) nodes = document.getElementsByTagName(localName);
        List<String> values = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = nodes.item(i).getTextContent();
            if (value != null && !value.isBlank()) values.add(value.trim());
        }
        return List.copyOf(values);
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static boolean isRetryableFault(String code) {
        if (code == null) return true;
        String normalized = code.toLowerCase();
        return normalized.contains("server") || normalized.contains("timeout") || normalized.contains("unavailable");
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null || error.getMessage().isBlank()
            ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record SoapResponse(int statusCode, String body) {}
    private record SoapFault(String code, String message) {}

    public record SendBillResult(
        boolean accepted,
        boolean observed,
        boolean retryable,
        boolean processing,
        String responseCode,
        String responseMsg,
        String cdrXml,
        String ticket,
        List<String> observations
    ) {
        public static SendBillResult transportError(String code, String message, boolean retryable) {
            return new SendBillResult(false, false, retryable, false, code, message, null, null, List.of());
        }

        public static SendBillResult processing(String ticket) {
            return new SendBillResult(false, false, false, true, "98", "En proceso", null, ticket, List.of());
        }
    }

    public static class SunatClientException extends RuntimeException {
        private final String code;
        private final boolean retryable;

        public SunatClientException(String code, String message, boolean retryable) {
            super(message);
            this.code = code;
            this.retryable = retryable;
        }

        public SunatClientException(String code, String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.retryable = retryable;
        }

        public String code() { return code; }
        public boolean retryable() { return retryable; }
    }
}
