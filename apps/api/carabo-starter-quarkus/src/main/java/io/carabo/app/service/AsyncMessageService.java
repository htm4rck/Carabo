package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.core.dto.EmitDocumentRequest;
import io.carabo.core.entity.AsyncMessageAudit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AsyncMessageService {
    @Inject EntityManager em;
    @Inject ObjectMapper objectMapper;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public UUID received(EmitDocumentRequest request) {
        try {
            AsyncMessageAudit audit = new AsyncMessageAudit();
            audit.setBusinessCode(request.getBusinessCode());
            audit.setExternalId(request.getExternalId());
            audit.setPayload(objectMapper.writeValueAsString(request));
            audit.setStatus("RECEIVED");
            audit.setDeliveryCount(1);
            em.persist(audit);
            em.flush();
            return audit.getId();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo auditar el mensaje asíncrono", e);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void finish(UUID id, String status, String error) {
        AsyncMessageAudit audit = em.find(AsyncMessageAudit.class, id);
        if (audit == null) return;
        audit.setStatus(status);
        audit.setErrorMessage(error);
        em.merge(audit);
    }

    public List<MessageSummary> list(String businessCode, String status, int limit) {
        String jpql = "SELECT a FROM AsyncMessageAudit a WHERE a.businessCode = :bc"
            + (status == null || status.isBlank() ? "" : " AND a.status = :status")
            + " ORDER BY a.updatedAt DESC";
        var query = em.createQuery(jpql, AsyncMessageAudit.class).setParameter("bc", businessCode)
            .setMaxResults(Math.min(Math.max(limit, 1), 200));
        if (status != null && !status.isBlank()) query.setParameter("status", status.toUpperCase());
        return query.getResultList().stream().map(this::summary).toList();
    }

    public Optional<EmitDocumentRequest> payloadForReplay(UUID id, String businessCode) {
        return find(id, businessCode).filter(a -> "DEAD".equals(a.getStatus()))
            .map(a -> {
                try { return objectMapper.readValue(a.getPayload(), EmitDocumentRequest.class); }
                catch (Exception e) { throw new IllegalStateException("Payload de DLQ inválido", e); }
            });
    }

    @Transactional
    public boolean markReplayed(UUID id, String businessCode) {
        var found = find(id, businessCode);
        if (found.isEmpty() || !"DEAD".equals(found.get().getStatus())) return false;
        var audit = found.get();
        audit.setStatus("REPLAYED");
        audit.setReplayCount(audit.getReplayCount() + 1);
        audit.setErrorMessage(null);
        em.merge(audit);
        return true;
    }

    private Optional<AsyncMessageAudit> find(UUID id, String businessCode) {
        return em.createQuery("SELECT a FROM AsyncMessageAudit a WHERE a.id = :id AND a.businessCode = :bc", AsyncMessageAudit.class)
            .setParameter("id", id).setParameter("bc", businessCode).getResultStream().findFirst();
    }

    private MessageSummary summary(AsyncMessageAudit a) {
        return new MessageSummary(a.getId(), a.getExternalId(), a.getStatus(), a.getErrorMessage(),
            a.getDeliveryCount(), a.getReplayCount(), a.getCreatedAt(), a.getUpdatedAt());
    }

    public record MessageSummary(UUID id, String externalId, String status, String errorMessage,
                                 int deliveryCount, int replayCount,
                                 java.time.Instant createdAt, java.time.Instant updatedAt) {}
}
