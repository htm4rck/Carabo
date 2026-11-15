package io.carabo.app.service;

import io.carabo.core.entity.BatchOperation;
import io.carabo.core.entity.Document;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.SendMode;
import io.carabo.sunat.SunatBillService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.Set;
import java.util.UUID;

/** Short local transactions around a receiver query; no remote call holds the database open. */
@ApplicationScoped
public class BatchTicketStore {
    @Inject EntityManager em;
    @Inject TenantService tenants;
    @Inject FiscalClock clock;
    static final Set<String> TERMINAL = Set.of("ACCEPTED", "OBSERVED", "REJECTED");

    record Snapshot(UUID operationId, String businessCode, String ticket, SendMode mode, String endpoint,
                    String solUser, String passwordRef, String oseUrl, String oseTokenRef, BatchService.BatchResult terminal) {}
    record Outcome(boolean accepted, String status, String message, String cdr) {}

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Snapshot prepare(String businessCode, String ticket) {
        var tenant = tenants.resolve(businessCode);
        var operation = em.createQuery("SELECT b FROM BatchOperation b WHERE b.businessCode=:bc AND b.ticket=:ticket ORDER BY b.createdAt DESC", BatchOperation.class)
            .setParameter("bc", businessCode).setParameter("ticket", ticket).setMaxResults(1)
            .getResultStream().findFirst().orElseThrow(() -> new NotFoundException("Ticket no encontrado en la empresa autenticada"));
        String endpoint = tenant.getSunatEndpoint();
        if (endpoint == null || endpoint.isBlank()) endpoint = "PRODUCTION".equalsIgnoreCase(tenant.getEnvironment()) ? SunatBillService.PROD_URL : SunatBillService.BETA_URL;
        return new Snapshot(operation.getId(), businessCode, ticket, tenant.getSendMode(), endpoint,
            tenant.getSolUser(), tenant.getSolPassword(), tenant.getOseBaseUrl(), tenant.getOseApiToken(),
            TERMINAL.contains(operation.getStatus()) ? result(operation) : null);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public BatchService.BatchResult complete(Snapshot snapshot, Outcome outcome) {
        var operation = em.find(BatchOperation.class, snapshot.operationId(), LockModeType.PESSIMISTIC_WRITE);
        if (operation == null || !snapshot.businessCode().equals(operation.getBusinessCode()) || !snapshot.ticket().equals(operation.getTicket()))
            throw new NotFoundException("Ticket no encontrado en la empresa autenticada");
        if (TERMINAL.contains(operation.getStatus())) return result(operation);
        if (!Set.of("ACCEPTED", "OBSERVED", "REJECTED", "PENDING", "ERROR").contains(outcome.status())
            || outcome.accepted() != Set.of("ACCEPTED", "OBSERVED").contains(outcome.status())) throw new IllegalArgumentException("Invalid fiscal query outcome");
        operation.setStatus(outcome.status()); operation.setMessage(outcome.message()); operation.setCdrXml(outcome.cdr());
        if (operation.getDocumentIds() != null && (outcome.accepted() || "REJECTED".equals(outcome.status()))) {
            for (String id : operation.getDocumentIds().split(",")) {
                var doc = em.find(Document.class, UUID.fromString(id), LockModeType.PESSIMISTIC_WRITE);
                if (doc != null && snapshot.businessCode().equals(doc.getBusinessCode()) && doc.getStatus() == DocumentStatus.VOID_REQUESTED)
                    doc.setStatus(outcome.accepted() ? DocumentStatus.VOIDED : DocumentStatus.ACCEPTED);
            }
        }
        em.flush();
        return result(operation);
    }

    private BatchService.BatchResult result(BatchOperation operation) {
        return new BatchService.BatchResult(operation.getId(), operation.getKind(), operation.getIdentifier(),
            Set.of("ACCEPTED", "OBSERVED").contains(operation.getStatus()), operation.getTicket(), operation.getStatus(),
            operation.getMessage(), operation.getDocumentCount(), operation.getReferenceDate(),
            operation.getUpdatedAt() != null ? operation.getUpdatedAt() : clock.nowUtc(), clock.zoneId(), operation.getDocumentIds() != null);
    }
}
