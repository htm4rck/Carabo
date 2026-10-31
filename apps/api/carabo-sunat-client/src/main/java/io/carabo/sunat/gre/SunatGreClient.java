package io.carabo.sunat.gre;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** SUNAT GRE REST transport. HTTP success and tickets do not imply fiscal acceptance. */
public class SunatGreClient {
    private final String clientId, clientSecret, solUser, solPassword, tokenUrl, sendUrl, statusUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private String cachedToken;
    private long tokenExpiry;

    public SunatGreClient(String clientId, String clientSecret, String solUser, String solPassword, boolean beta) {
        this(clientId, clientSecret, solUser, solPassword,
            String.format(beta ? "https://gre-beta.sunat.gob.pe/v1/clientessol/%s/oauth2/token"
                : "https://api-seguridad.sunat.gob.pe/v1/clientessol/%s/oauth2/token", clientId),
            beta ? "https://gre-beta.sunat.gob.pe/v1/contribuyente/gem/comprobantes/"
                : "https://api-cpe.sunat.gob.pe/v1/contribuyente/gem/comprobantes/",
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    SunatGreClient(String clientId, String clientSecret, String solUser, String solPassword,
                   String tokenUrl, String sendUrl, HttpClient httpClient) {
        this.clientId = clientId; this.clientSecret = clientSecret; this.solUser = solUser;
        this.solPassword = solPassword; this.tokenUrl = tokenUrl; this.sendUrl = sendUrl;
        this.statusUrl = sendUrl + "envios/"; this.httpClient = httpClient;
    }

    public GreSendResult send(String filename, String signedXml) {
        String token;
        try { token = getToken(); }
        catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return new GreSendResult(false, null, "No se pudo autenticar con SUNAT GRE; revisa credenciales y disponibilidad", true, false);
        }
        try {
            byte[] zip = ZipUtil.zipContent(filename + ".xml", signedXml.getBytes(StandardCharsets.UTF_8));
            String body = mapper.writeValueAsString(Map.of("archivo", Map.of(
                "nomArchivo", filename + ".zip", "arcGreZip", Base64.getEncoder().encodeToString(zip),
                "hashZip", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip)))));
            var request = HttpRequest.newBuilder(URI.create(sendUrl + filename)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                String ticket = mapper.readTree(response.body()).path("numTicket").asText("");
                if (!ticket.isBlank()) return new GreSendResult(true, ticket, null, false, false);
                return unknownSubmission();
            }
            if (response.statusCode() >= 500) return unknownSubmission();
            return new GreSendResult(false, null, "SUNAT no admitió la solicitud GRE (HTTP " + response.statusCode()
                + "); revisa la configuración y el documento", false, false);
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return unknownSubmission();
        }
    }

    private GreSendResult unknownSubmission() {
        return new GreSendResult(false, null, "Envío sin confirmación de ticket. Requiere conciliación antes de reenviar; no habilita el traslado", false, true);
    }

    public GreStatusResult checkStatus(String ticket) {
        try {
            var request = HttpRequest.newBuilder(URI.create(statusUrl + URLEncoder.encode(ticket, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + getToken()).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return statusError("HTTP_" + response.statusCode(), "SUNAT no pudo confirmar el estado de la guía");
            return parseStatus(response.body());
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return statusError("TRANSPORT_ERROR", "No se pudo consultar SUNAT. La guía sigue sin confirmación fiscal");
        }
    }

    GreStatusResult parseStatus(String body) throws Exception {
        JsonNode node = mapper.readTree(body);
        String status = node.path("codRespuesta").asText("");
        if ("98".equals(status)) return new GreStatusResult(false, "98", "SUNAT está procesando la guía; no habilita el traslado", null, false, false);
        String encoded = node.path("arcCdr").asText("");
        if (encoded.isBlank()) return statusError("MISSING_CDR", "SUNAT aún no devolvió una CDR verificable; no habilita el traslado");
        String xml = ZipUtil.unzipFirst(Base64.getDecoder().decode(encoded));
        if (xml == null) return statusError("INVALID_CDR", "La respuesta SUNAT no contiene una CDR verificable");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var responses = document.getElementsByTagNameNS("*", "ResponseCode");
        var descriptions = document.getElementsByTagNameNS("*", "Description");
        if (responses.getLength() != 1) return statusError("INVALID_CDR", "La CDR no contiene un resultado fiscal único");
        String code = responses.item(0).getTextContent().trim();
        if (!code.matches("\\d{1,5}")) return statusError("INVALID_CDR", "La CDR no contiene un código fiscal válido");
        String message = descriptions.getLength() > 0 ? descriptions.item(0).getTextContent().trim() : "Respuesta fiscal " + code;
        boolean accepted = "0".equals(code);
        var notes = document.getElementsByTagNameNS("*", "Note");
        boolean observed = accepted && notes.getLength() > 0;
        for (int index = 0; index < Math.min(notes.getLength(), 20); index++) message += "; " + notes.item(index).getTextContent().trim();
        if (message.length() > 2000) message = message.substring(0, 2000);
        return new GreStatusResult(accepted, code, message, xml, !accepted, observed);
    }

    private GreStatusResult statusError(String code, String message) {
        return new GreStatusResult(false, code, message, null, false, false);
    }

    private String getToken() throws Exception {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) return cachedToken;
        String form = "grant_type=password&scope=https://api-cpe.sunat.gob.pe&client_id=" + encode(clientId)
            + "&client_secret=" + encode(clientSecret) + "&username=" + encode(solUser) + "&password=" + encode(solPassword);
        var request = HttpRequest.newBuilder(URI.create(tokenUrl)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("GRE authentication unavailable");
        var node = mapper.readTree(response.body());
        cachedToken = node.path("access_token").asText("");
        if (cachedToken.isBlank()) throw new IllegalStateException("GRE token missing");
        tokenExpiry = System.currentTimeMillis() + Math.max(0, node.path("expires_in").asInt(3600) - 60) * 1000L;
        return cachedToken;
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public record GreSendResult(boolean success, String ticket, String error, boolean retryable, boolean uncertain) {}
    public record GreStatusResult(boolean accepted, String code, String message, String cdrXml, boolean rejected, boolean observed) {}
}
