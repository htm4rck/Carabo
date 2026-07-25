package io.carabo.ose;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * HTTP client for OSE (Operador de Servicios Electrónicos).
 * Supports Nubefact-compatible API (also works with Bizlinks, PSE providers).
 *
 * OSE receives the signed XML and handles SUNAT submission on behalf of the issuer.
 * Response includes CDR and acceptance status.
 */
public class OseClient {

    private final String baseUrl;
    private final String apiToken;
    private final HttpClient httpClient;

    public OseClient(String baseUrl, String apiToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Sends a signed XML document to the OSE for processing.
     * The OSE validates, forwards to SUNAT, and returns the result.
     */
    public OseResult send(String fileName, String signedXml) {
        try {
            String base64Content = Base64.getEncoder().encodeToString(signedXml.getBytes());

            String jsonBody = """
                {
                  "fileName": "%s",
                  "contentFile": "%s"
                }
                """.formatted(fileName, base64Content);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/documents"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiToken)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return parseSuccess(response.body());
            } else {
                return OseResult.error(response.statusCode(), response.body());
            }
        } catch (Exception e) {
            return OseResult.error(0, e.getMessage());
        }
    }

    /**
     * Queries the status of a previously sent document.
     */
    public OseResult getStatus(String externalId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/documents/" + externalId + "/status"))
                .header("Authorization", "Bearer " + apiToken)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseSuccess(response.body());
            } else {
                return OseResult.error(response.statusCode(), response.body());
            }
        } catch (Exception e) {
            return OseResult.error(0, e.getMessage());
        }
    }

    /**
     * Downloads the CDR XML for a processed document.
     */
    public String downloadCdr(String externalId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/documents/" + externalId + "/cdr"))
                .header("Authorization", "Bearer " + apiToken)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("CDR download failed: " + e.getMessage(), e);
        }
    }

    private OseResult parseSuccess(String body) {
        // Simple JSON parsing (no external dependency)
        boolean accepted = body.contains("\"accepted\":true") || body.contains("\"status\":\"ACCEPTED\"");
        String responseCode = extractJsonField(body, "responseCode");
        String responseMsg = extractJsonField(body, "responseMessage");
        String digestValue = extractJsonField(body, "hashCode");
        String cdrBase64 = extractJsonField(body, "cdrContent");

        String cdrXml = null;
        if (cdrBase64 != null && !cdrBase64.isEmpty()) {
            cdrXml = new String(Base64.getDecoder().decode(cdrBase64));
        }

        return new OseResult(accepted, responseCode, responseMsg, digestValue, cdrXml, 200);
    }

    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    public record OseResult(boolean accepted, String responseCode, String responseMsg, String digestValue, String cdrXml, int httpStatus) {
        public static OseResult error(int httpStatus, String msg) {
            return new OseResult(false, "ERROR", msg, null, null, httpStatus);
        }
    }
}
