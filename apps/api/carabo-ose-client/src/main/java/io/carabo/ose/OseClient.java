package io.carabo.ose;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for the explicit Carabo OSE Gateway JSON contract.
 * Provider-specific OSE APIs must be implemented as adapters; this class does not
 * claim wire compatibility with Nubefact, Bizlinks, PSE or any other provider.
 */
public class OseClient {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(30);

    private final URI baseUrl;
    private final String apiToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OseClient(String baseUrl, String apiToken) {
        this(baseUrl, apiToken, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), new ObjectMapper());
    }

    public OseClient(String baseUrl, String apiToken, HttpClient httpClient, ObjectMapper objectMapper) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("OSE base URL is required");
        if (apiToken == null || apiToken.isBlank()) throw new IllegalArgumentException("OSE API token is required");
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUrl = URI.create(normalized);
        this.apiToken = apiToken;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /** Sends a signed XML using the Carabo OSE Gateway v1 contract. */
    public OseResult send(String fileName, String signedXml) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("fileName", fileName);
            body.put("contentFile", Base64.getEncoder().encodeToString(signedXml.getBytes(StandardCharsets.UTF_8)));
            HttpRequest request = request("/api/v1/documents")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", fileName)
                .timeout(SEND_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            return handleJson(httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OseResult.transportError("INTERRUPTED", "OSE request interrupted", true, 0);
        } catch (Exception e) {
            return OseResult.transportError("TRANSPORT_ERROR", safeMessage(e), true, 0);
        }
    }

    public OseResult getStatus(String externalId) {
        try {
            HttpRequest request = request("/api/v1/documents/" + path(externalId) + "/status")
                .timeout(QUERY_TIMEOUT).GET().build();
            return handleJson(httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OseResult.transportError("INTERRUPTED", "OSE request interrupted", true, 0);
        } catch (Exception e) {
            return OseResult.transportError("TRANSPORT_ERROR", safeMessage(e), true, 0);
        }
    }

    public CdrResult downloadCdr(String externalId) {
        try {
            HttpRequest request = request("/api/v1/documents/" + path(externalId) + "/cdr")
                .header("Accept", "application/xml, application/json")
                .timeout(QUERY_TIMEOUT).GET().build();
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                if (contentType.contains("json")) {
                    JsonNode json = objectMapper.readTree(response.body());
                    String encoded = text(json, "cdrContent");
                    if (encoded == null) return CdrResult.error("MISSING_CDR", "OSE response did not include a CDR", true, response.statusCode());
                    return CdrResult.success(new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8), response.statusCode());
                }
                return CdrResult.success(response.body(), response.statusCode());
            }
            return CdrResult.error("HTTP_" + response.statusCode(), errorMessage(response.body()),
                isRetryableHttp(response.statusCode()), response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CdrResult.error("INTERRUPTED", "OSE request interrupted", true, 0);
        } catch (Exception e) {
            return CdrResult.error("TRANSPORT_ERROR", safeMessage(e), true, 0);
        }
    }

    private OseResult handleJson(HttpResponse<String> response) {
        int httpStatus = response.statusCode();
        if (httpStatus < 200 || httpStatus >= 300) {
            return OseResult.transportError("HTTP_" + httpStatus, errorMessage(response.body()),
                isRetryableHttp(httpStatus), httpStatus);
        }
        try {
            JsonNode json = objectMapper.readTree(response.body());
            String status = upper(text(json, "status"));
            boolean accepted = json.path("accepted").asBoolean(false) || "ACCEPTED".equals(status) || "OBSERVED".equals(status);
            boolean observed = "OBSERVED".equals(status) || json.path("observed").asBoolean(false);
            boolean processing = "PENDING".equals(status) || "PROCESSING".equals(status) || "QUEUED".equals(status);
            boolean retryable = json.path("retryable").asBoolean("ERROR".equals(status) || "FAILED".equals(status));
            String responseCode = first(text(json, "responseCode"), text(json, "code"));
            String responseMessage = first(text(json, "responseMessage"), text(json, "message"));
            String digestValue = first(text(json, "digestValue"), text(json, "hashCode"));
            String externalId = first(text(json, "externalId"), text(json, "id"));
            String cdrXml = decodeOptional(text(json, "cdrContent"));
            if (!accepted && !processing && (status == null || status.isBlank())) {
                return OseResult.transportError("INVALID_RESPONSE", "OSE response did not include a status", true, httpStatus);
            }
            return new OseResult(accepted, observed, processing, retryable, responseCode,
                responseMessage, digestValue, cdrXml, externalId, httpStatus);
        } catch (Exception e) {
            return OseResult.transportError("INVALID_RESPONSE", "OSE returned invalid JSON", true, httpStatus);
        }
    }

    private HttpRequest.Builder request(String relativePath) {
        return HttpRequest.newBuilder(URI.create(baseUrl.toString() + relativePath))
            .header("Authorization", "Bearer " + apiToken)
            .header("Accept", "application/json");
    }

    private String errorMessage(String body) {
        if (body == null || body.isBlank()) return "OSE returned an empty error response";
        try {
            JsonNode json = objectMapper.readTree(body);
            String value = first(text(json, "message"), text(json, "error"));
            return value != null ? value : "OSE request failed";
        } catch (Exception ignored) {
            return body.length() <= 500 ? body : body.substring(0, 500);
        }
    }

    private static String decodeOptional(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        return new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private static String path(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("OSE external id is required");
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String first(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private static boolean isRetryableHttp(int status) {
        return status == 0 || status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null || error.getMessage().isBlank()
            ? error.getClass().getSimpleName() : error.getMessage();
    }

    public record OseResult(
        boolean accepted,
        boolean observed,
        boolean processing,
        boolean retryable,
        String responseCode,
        String responseMsg,
        String digestValue,
        String cdrXml,
        String externalId,
        int httpStatus
    ) {
        public static OseResult transportError(String code, String message, boolean retryable, int httpStatus) {
            return new OseResult(false, false, false, retryable, code, message, null, null, null, httpStatus);
        }
    }

    public record CdrResult(boolean success, String cdrXml, String code, String message, boolean retryable, int httpStatus) {
        public static CdrResult success(String cdrXml, int httpStatus) {
            return new CdrResult(true, cdrXml, null, null, false, httpStatus);
        }

        public static CdrResult error(String code, String message, boolean retryable, int httpStatus) {
            return new CdrResult(false, null, code, message, retryable, httpStatus);
        }
    }
}
