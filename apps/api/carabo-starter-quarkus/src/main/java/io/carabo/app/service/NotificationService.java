package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.app.security.SecretResolver;
import io.carabo.core.dto.DocumentCallback;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.NotificationOutbox;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@ApplicationScoped
public class NotificationService {
    private static final Logger LOG = Logger.getLogger(NotificationService.class);
    private static final int MAX_ATTEMPTS = 8;

    @Inject EntityManager em;
    @Inject ObjectMapper objectMapper;
    @Inject TenantService tenantService;
    @Inject SecretResolver secretResolver;
    @Inject OperationalMetrics metrics;

    @ConfigProperty(name = "carabo.webhooks.allow-private-addresses", defaultValue = "false")
    boolean allowPrivateAddresses;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();

    @Transactional
    public void enqueueFor(Document document, Tenant tenant) {
        if (document.getCallbackUrl() != null && !document.getCallbackUrl().isBlank()) {
            enqueue(document, "CALLBACK", document.getCallbackUrl());
        }
        if (tenant.getWebhookUrl() != null && !tenant.getWebhookUrl().isBlank()) {
            enqueue(document, "WEBHOOK", tenant.getWebhookUrl());
        }
    }

    private void enqueue(Document document, String targetType, String targetUrl) {
        String eventKey = sha256(document.getId() + ":" + document.getStatus() + ":" + targetType + ":" + targetUrl);
        Long existing = em.createQuery(
                "SELECT COUNT(o) FROM NotificationOutbox o WHERE o.eventKey = :key", Long.class)
            .setParameter("key", eventKey).getSingleResult();
        if (existing > 0) return;

        try {
            DocumentCallback callback = isFiscalSuccess(document.getStatus())
                ? DocumentCallback.success(document.getId(), document.getExternalId(), document.getFullNumber(),
                    document.getStatus().name(), document.getHashCode())
                : DocumentCallback.failure(document.getId(), document.getExternalId(), document.getStatus().name(), null, document.getSunatResponse());
            NotificationOutbox item = new NotificationOutbox();
            item.setEventKey(eventKey);
            item.setBusinessCode(document.getBusinessCode());
            item.setDocumentId(document.getId());
            item.setTargetType(targetType);
            item.setTargetUrl(targetUrl);
            item.setPayload(objectMapper.writeValueAsString(callback));
            try {
                validateTarget(targetUrl);
            } catch (IllegalArgumentException invalidTarget) {
                item.setStatus("DEAD");
                item.setLastError(invalidTarget.getMessage());
            }
            em.persist(item);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo registrar la notificación durable", e);
        }
    }

    @Transactional
    public int deliverDue() {
        List<NotificationOutbox> pending = em.createQuery(
                "SELECT o FROM NotificationOutbox o WHERE o.status IN ('PENDING','RETRY') " +
                    "AND o.nextAttemptAt <= :now ORDER BY o.nextAttemptAt", NotificationOutbox.class)
            .setParameter("now", Instant.now()).setMaxResults(50).getResultList();
        for (NotificationOutbox item : pending) deliver(item);
        return pending.size();
    }

    private void deliver(NotificationOutbox item) {
        int attempt = item.getAttempts() + 1;
        item.setAttempts(attempt);
        try {
            validateTarget(item.getTargetUrl());
            long timestamp = Instant.now().getEpochSecond();
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(item.getTargetUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                // Operation is multi-tenant and requires this header to resolve
                // the target schema for an asynchronous emission callback.
                .header("business-code", item.getBusinessCode())
                .header("X-Carabo-Event-Id", item.getEventKey())
                .header("X-Carabo-Timestamp", Long.toString(timestamp));
            if ("WEBHOOK".equals(item.getTargetType())) {
                Tenant tenant = tenantService.resolve(item.getBusinessCode());
                if (tenant.getWebhookSecret() != null && !tenant.getWebhookSecret().isBlank()) {
                    String secret = secretResolver.resolve(tenant.getWebhookSecret());
                    request.header("X-Carabo-Signature", "v1=" + hmacHex(timestamp + "." + item.getPayload(), secret));
                }
            }
            if ("CALLBACK".equals(item.getTargetType())) {
                Tenant tenant = tenantService.resolve(item.getBusinessCode());
                if (tenant.getWebhookSecret() != null && !tenant.getWebhookSecret().isBlank())
                    request.header("X-Carabo-Callback-Key", secretResolver.resolve(tenant.getWebhookSecret()));
            }
            HttpResponse<String> response = httpClient.send(
                request.POST(HttpRequest.BodyPublishers.ofString(item.getPayload())).build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            item.setStatus("DELIVERED");
            metrics.notification("delivered");
            item.setDeliveredAt(Instant.now());
            item.setLastError(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(item, attempt, "Entrega interrumpida");
        } catch (Exception e) {
            fail(item, attempt, safeMessage(e));
        }
        em.merge(item);
    }

    private void fail(NotificationOutbox item, int attempt, String message) {
        item.setLastError(message);
        if (attempt >= MAX_ATTEMPTS) {
            item.setStatus("DEAD");
            metrics.notification("dead");
            LOG.errorf("Notification %s exhausted after %d attempts: %s", item.getEventKey(), attempt, message);
        } else {
            item.setStatus("RETRY");
            metrics.notification("retry");
            long minutes = switch (attempt) { case 1 -> 1; case 2 -> 5; case 3 -> 15; default -> 60; };
            item.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(minutes)));
        }
    }

    public List<NotificationSummary> list(String businessCode, int limit) {
        return em.createQuery(
                "SELECT o FROM NotificationOutbox o WHERE o.businessCode = :bc ORDER BY o.createdAt DESC",
                NotificationOutbox.class)
            .setParameter("bc", businessCode).setMaxResults(Math.min(Math.max(limit, 1), 200))
            .getResultList().stream().map(o -> new NotificationSummary(o.getId().toString(), o.getDocumentId().toString(),
                o.getTargetType(), o.getStatus(), o.getAttempts(), o.getLastError(), o.getNextAttemptAt(),
                o.getCreatedAt(), o.getDeliveredAt())).toList();
    }

    @Transactional
    public boolean retry(String id, String businessCode) {
        NotificationOutbox item;
        try {
            item = em.createQuery(
                    "SELECT o FROM NotificationOutbox o WHERE o.id = :id AND o.businessCode = :bc",
                    NotificationOutbox.class)
                .setParameter("id", java.util.UUID.fromString(id)).setParameter("bc", businessCode)
                .getResultStream().findFirst().orElse(null);
        } catch (IllegalArgumentException invalidId) {
            return false;
        }
        if (item == null || "DELIVERED".equals(item.getStatus())) return false;
        item.setStatus("RETRY");
        item.setAttempts(0);
        item.setLastError(null);
        item.setNextAttemptAt(Instant.now());
        em.merge(item);
        return true;
    }

    private void validateTarget(String value) {
        URI uri = URI.create(value);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("El callback debe ser una URL HTTP(S) válida");
        }
        if (allowPrivateAddresses) return;
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("El callback no puede apuntar a una red privada");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("No se pudo resolver el host del callback", e);
        }
    }

    private boolean isFiscalSuccess(DocumentStatus status) {
        return status == DocumentStatus.ACCEPTED || status == DocumentStatus.OBSERVED;
    }

    private String hmacHex(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String safeMessage(Exception e) { return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(); }

    public record NotificationSummary(String id, String documentId, String type, String status,
                                      int attempts, String lastError, Instant nextAttemptAt,
                                      Instant createdAt, Instant deliveredAt) {}
}
