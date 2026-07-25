package io.carabo.sunat.gre;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SUNAT GRE 2.0 REST API client.
 * Uses OAuth2 client_credentials for token, then sends signed XML via REST.
 *
 * Endpoints:
 * - Token: https://api-seguridad.sunat.gob.pe/v1/clientessol/{clientId}/oauth2/token
 * - Send:  https://api-cpe.sunat.gob.pe/v1/contribuyente/gem/comprobantes/{filename}
 * - Status: https://api-cpe.sunat.gob.pe/v1/contribuyente/gem/comprobantes/envios/{ticket}
 */
public class SunatGreClient {

    private static final String TOKEN_URL_TEMPLATE = "https://api-seguridad.sunat.gob.pe/v1/clientessol/%s/oauth2/token";
    private static final String SEND_URL = "https://api-cpe.sunat.gob.pe/v1/contribuyente/gem/comprobantes/";
    private static final String STATUS_URL = "https://api-cpe.sunat.gob.pe/v1/contribuyente/gem/comprobantes/envios/";

    // Beta endpoints
    private static final String BETA_TOKEN_URL_TEMPLATE = "https://gre-beta.sunat.gob.pe/v1/clientessol/%s/oauth2/token";
    private static final String BETA_SEND_URL = "https://gre-beta.sunat.gob.pe/v1/contribuyente/gem/comprobantes/";
    private static final String BETA_STATUS_URL = "https://gre-beta.sunat.gob.pe/v1/contribuyente/gem/comprobantes/envios/";

    private final String clientId;
    private final String clientSecret;
    private final String solUser;     // RUC + usuario SOL (e.g. 20123456789MODDATOS)
    private final String solPassword;
    private final boolean beta;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String cachedToken;
    private long tokenExpiry;

    public SunatGreClient(String clientId, String clientSecret, String solUser, String solPassword, boolean beta) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.solUser = solUser;
        this.solPassword = solPassword;
        this.beta = beta;
    }

    /**
     * Send signed GRE XML to SUNAT REST API.
     * Returns ticket number for async status check.
     */
    public GreSendResult send(String filename, String signedXml) {
        try {
            String token = getToken();
            byte[] zipBytes = ZipUtil.zipContent(filename + ".xml", signedXml.getBytes(StandardCharsets.UTF_8));
            String base64Zip = Base64.getEncoder().encodeToString(zipBytes);

            String jsonBody = mapper.writeValueAsString(new SendBody(filename, base64Zip));

            String sendUrl = beta ? BETA_SEND_URL + filename : SEND_URL + filename;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(sendUrl))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                JsonNode node = mapper.readTree(response.body());
                String ticket = node.has("numTicket") ? node.get("numTicket").asText() : null;
                return new GreSendResult(true, ticket, null, response.body());
            }

            return new GreSendResult(false, null, "HTTP " + response.statusCode(), response.body());
        } catch (Exception e) {
            return new GreSendResult(false, null, e.getMessage(), null);
        }
    }

    /**
     * Check ticket status for a previously sent GRE.
     */
    public GreStatusResult checkStatus(String ticket) {
        try {
            String token = getToken();
            String statusUrl = beta ? BETA_STATUS_URL + ticket : STATUS_URL + ticket;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(statusUrl))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode node = mapper.readTree(response.body());
                String status = node.has("codRespuesta") ? node.get("codRespuesta").asText() : "PENDING";
                boolean accepted = "0".equals(status) || "0000".equals(status);
                String cdrBase64 = node.has("arcCdr") ? node.get("arcCdr").asText() : null;
                byte[] cdrZip = cdrBase64 != null ? Base64.getDecoder().decode(cdrBase64) : null;
                String cdrXml = cdrZip != null ? ZipUtil.unzipFirst(cdrZip) : null;
                String msg = node.has("desMensaje") ? node.get("desMensaje").asText() : "";
                return new GreStatusResult(accepted, status, msg, cdrXml);
            }

            return new GreStatusResult(false, "ERROR", "HTTP " + response.statusCode(), null);
        } catch (Exception e) {
            return new GreStatusResult(false, "ERROR", e.getMessage(), null);
        }
    }

    private String getToken() throws Exception {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken;
        }

        String tokenUrl = String.format(beta ? BETA_TOKEN_URL_TEMPLATE : TOKEN_URL_TEMPLATE, clientId);

        String formBody = "grant_type=password"
            + "&scope=https://api-cpe.sunat.gob.pe"
            + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
            + "&username=" + URLEncoder.encode(solUser, StandardCharsets.UTF_8)
            + "&password=" + URLEncoder.encode(solPassword, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("SUNAT token request failed: " + response.statusCode() + " " + response.body());
        }

        JsonNode node = mapper.readTree(response.body());
        cachedToken = node.get("access_token").asText();
        int expiresIn = node.has("expires_in") ? node.get("expires_in").asInt() : 3600;
        tokenExpiry = System.currentTimeMillis() + (expiresIn - 60) * 1000L;

        return cachedToken;
    }

    // DTOs
    public record GreSendResult(boolean success, String ticket, String error, String rawResponse) {}
    public record GreStatusResult(boolean accepted, String code, String message, String cdrXml) {}

    private static class SendBody {
        public String nomArchivo;
        public String arcGreZip;
        public SendBody(String nomArchivo, String arcGreZip) {
            this.nomArchivo = nomArchivo;
            this.arcGreZip = arcGreZip;
        }
    }
}
