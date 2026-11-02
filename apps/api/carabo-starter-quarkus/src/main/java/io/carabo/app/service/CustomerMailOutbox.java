package io.carabo.app.service;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CustomerMailOutbox {
    @Inject EntityManager em;

    public static String validRecipient(String value) {
        if (value == null || value.isBlank()) return null;
        String email = value.trim();
        if (email.length() > 254 || !email.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+"))
            throw new IllegalArgumentException("Correo del receptor inválido");
        return email;
    }

    @Transactional
    public void enqueue(Document document) {
        if (document.getReceiverEmail() == null ||
            !(document.getStatus() == DocumentStatus.ACCEPTED || document.getStatus() == DocumentStatus.OBSERVED)) return;
        em.createNativeQuery("INSERT INTO carabo.notification_outbox(id,event_key,business_code,document_id,target_type,target_url,payload,status,attempts,next_attempt_at,created_at) " +
            "VALUES(:id,:key,:business,:document,'CUSTOMER_EMAIL',:recipient,'{}','PENDING',0,now(),now()) ON CONFLICT(event_key) DO NOTHING")
            .setParameter("id", UUID.randomUUID()).setParameter("key", "customer-email/" + document.getId())
            .setParameter("business", document.getBusinessCode()).setParameter("document", document.getId())
            .setParameter("recipient", document.getReceiverEmail()).executeUpdate();
    }

    @Transactional
    public void configureRecipient(UUID id, String business, String recipient) {
        String email;
        try { email = validRecipient(recipient); }
        catch (IllegalArgumentException invalid) { throw new BadRequestException("Correo del receptor inválido"); }
        if (email == null) throw new BadRequestException("Indica el correo del cliente");
        Document document = em.createQuery("SELECT d FROM Document d WHERE d.id=:id AND d.businessCode=:business", Document.class)
            .setParameter("id", id).setParameter("business", business).setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
            .getResultStream().findFirst().orElseThrow(NotFoundException::new);
        if (document.getReceiverEmail() != null && !document.getReceiverEmail().equalsIgnoreCase(email))
            throw new jakarta.ws.rs.ClientErrorException("El comprobante ya tiene un destinatario registrado", 409);
        document.setReceiverEmail(email);
        enqueue(document);
    }

    @Transactional
    public Claim claim() {
        em.createNativeQuery("UPDATE carabo.notification_outbox SET status='DEAD',last_error='MAIL_RETRY_EXHAUSTED' " +
            "WHERE target_type='CUSTOMER_EMAIL' AND status='PROCESSING' AND lease_until<now() AND attempts>=8").executeUpdate();
        UUID lease = UUID.randomUUID();
        List<?> rows = em.createNativeQuery("WITH due AS (SELECT id FROM carabo.notification_outbox WHERE target_type='CUSTOMER_EMAIL' AND attempts<8 " +
            "AND ((status IN ('PENDING','RETRY') AND next_attempt_at<=now()) OR (status='PROCESSING' AND lease_until<now())) " +
            "ORDER BY next_attempt_at,id FOR UPDATE SKIP LOCKED LIMIT 1) " +
            "UPDATE carabo.notification_outbox n SET status='PROCESSING',attempts=attempts+1,lease_id=:lease,lease_until=now()+interval '2 minutes' " +
            "FROM due WHERE n.id=due.id RETURNING n.id,n.business_code,n.document_id,n.target_url,n.payload,n.attempts")
            .setParameter("lease", lease).getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = (Object[]) rows.get(0);
        return new Claim(UUID.fromString(row[0].toString()), lease, row[1].toString(), UUID.fromString(row[2].toString()), row[3].toString(), row[4].toString(), ((Number) row[5]).intValue());
    }

    @Transactional
    public MailDocument document(Claim claim) {
        Document document = em.createQuery("SELECT d FROM Document d WHERE d.id=:id AND d.businessCode=:business", Document.class)
            .setParameter("id", claim.documentId()).setParameter("business", claim.business()).getSingleResult();
        if (!(document.getStatus() == DocumentStatus.ACCEPTED || document.getStatus() == DocumentStatus.OBSERVED))
            throw new IllegalStateException("MAIL_DOCUMENT_NOT_ACCEPTED");
        Tenant tenant = em.createQuery("SELECT t FROM Tenant t WHERE t.businessCode=:business", Tenant.class)
            .setParameter("business", claim.business()).getSingleResult();
        return new MailDocument(document.getSeries(), document.getNumber(), document.getFullNumber(), document.getIssuerRuc(),
            document.getIssuerName(), document.getDocType().sunatCode(), document.getTotal().toPlainString(), document.getCurrency(),
            !"PRODUCTION".equals(tenant.getEnvironment()));
    }

    @Transactional
    public Document pdfDocument(Claim claim) {
        Document document = em.createQuery("SELECT d FROM Document d LEFT JOIN FETCH d.lines WHERE d.id=:id AND d.businessCode=:business", Document.class)
            .setParameter("id", claim.documentId()).setParameter("business", claim.business()).getSingleResult();
        if (!(document.getStatus() == DocumentStatus.ACCEPTED || document.getStatus() == DocumentStatus.OBSERVED))
            throw new IllegalStateException("MAIL_DOCUMENT_NOT_ACCEPTED");
        return document;
    }

    @Transactional
    public boolean prepared(Claim claim, String payload) {
        return update(claim, "payload=:payload", payload) == 1;
    }

    @Transactional
    public void finish(Claim claim, boolean queued) {
        finish(claim, queued, "MAIL_PUBLICATION_FAILED");
    }

    @Transactional
    public void finish(Claim claim, boolean queued, String errorCode) {
        em.createNativeQuery("UPDATE carabo.notification_outbox SET status=:status,last_error=:error,next_attempt_at=:next,lease_until=NULL," +
            "payload=CASE WHEN :queued THEN '{}' ELSE payload END WHERE id=:id AND lease_id=:lease AND status='PROCESSING'")
            .setParameter("status", queued ? "QUEUED" : claim.attempts() >= 8 ? "DEAD" : "RETRY")
            .setParameter("error", queued ? null : errorCode)
            .setParameter("next", Instant.now().plusSeconds(Math.min(3600, 30L << (claim.attempts() - 1))))
            .setParameter("queued", queued).setParameter("id", claim.id()).setParameter("lease", claim.lease()).executeUpdate();
    }

    private int update(Claim claim, String assignment, String payload) {
        return em.createNativeQuery("UPDATE carabo.notification_outbox SET " + assignment + " WHERE id=:id AND lease_id=:lease AND status='PROCESSING' AND lease_until>now()")
            .setParameter("payload", payload).setParameter("id", claim.id()).setParameter("lease", claim.lease()).executeUpdate();
    }

    public record Claim(UUID id, UUID lease, String business, UUID documentId, String recipient, String payload, int attempts) {}
    public record MailDocument(String series, Integer number, String fullNumber, String issuerRuc, String issuerName, String docType, String total, String currency, boolean test) {}
}
