package io.carabo.app.scheduler;

import io.carabo.app.service.BatchService;
import io.carabo.app.service.DocumentService;
import io.carabo.app.service.DispatchGuideService;
import io.carabo.app.service.DispatchGuideProcessor;
import io.carabo.app.service.FiscalClock;
import io.carabo.app.service.NotificationService;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class DocumentScheduler {

    private static final Logger LOG = Logger.getLogger(DocumentScheduler.class);

    @Inject
    DocumentService documentService;

    @Inject
    BatchService batchService;

    @Inject
    EntityManager em;

    @Inject
    FiscalClock fiscalClock;

    @Inject
    NotificationService notificationService;

    @Inject
    DispatchGuideService dispatchGuideService;

    @Scheduled(every = "30s", identity = "notification-outbox")
    void deliverNotifications() {
        int processed = notificationService.deliverDue();
        if (processed > 0) LOG.infof("Processed %d durable notifications", processed);
    }

    @Inject DispatchGuideProcessor dispatchGuideProcessor;

    @Scheduled(every = "15s", identity = "dispatch-guide-submit", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void submitDispatchGuides() {
        for (var guide : dispatchGuideService.findSubmissionsDue(20)) {
            try { dispatchGuideProcessor.process(guide.getId(), guide.getBusinessCode()); }
            catch (Exception error) { LOG.errorf("GRE processing interrupted for %s: %s", guide.getId(), error.getClass().getSimpleName()); }
        }
    }

    @Scheduled(every = "30s", identity = "dispatch-guide-ticket-poll", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void checkDispatchGuideTickets() {
        var guides = dispatchGuideService.findTicketsDue(50);
        for (var guide : guides) {
            try {
                dispatchGuideService.checkStatus(guide.getId(), guide.getBusinessCode());
            } catch (Exception e) {
                LOG.errorf(e, "GRE ticket check failed for %s", guide.getId());
            }
        }
        if (!guides.isEmpty()) LOG.infof("Checked %d GRE tickets", guides.size());
    }

    /**
     * Retries failed/pending documents every 5 minutes.
     */
    @Scheduled(every = "5m", identity = "document-retry")
    void retryFailedDocuments() {
        List<Document> retryable = documentService.findRetryable();
        if (retryable.isEmpty()) return;

        LOG.info("Retrying " + retryable.size() + " documents");
        for (Document doc : retryable) {
            try {
                documentService.retry(doc.getId());
            } catch (Exception e) {
                LOG.error("Retry failed for " + doc.getId(), e);
            }
        }
    }

    /**
     * Generates daily summaries for all active tenants at 6:00 AM.
     * Summarizes receipts (boletas) from the previous day.
     */
    @Scheduled(cron = "0 0 6 * * ?", timeZone = "America/Lima", identity = "daily-summary")
    void generateDailySummaries() {
        LocalDate yesterday = fiscalClock.today().minusDays(1);
        List<Tenant> tenants = getActiveTenants();

        LOG.info("Generating daily summaries for " + tenants.size() + " tenants, date: " + yesterday);
        for (Tenant tenant : tenants) {
            try {
                var result = batchService.sendDailySummary(tenant.getBusinessCode(), yesterday);
                if (result.success()) {
                    LOG.info("Summary sent for " + tenant.getBusinessCode() + ": " + result.message());
                }
            } catch (Exception e) {
                LOG.error("Daily summary failed for " + tenant.getBusinessCode(), e);
            }
        }
    }

    /**
     * Checks certificate expiry every day at 8:00 AM.
     * Logs warnings for certificates expiring within 30 days.
     */
    @Scheduled(cron = "0 0 8 * * ?", timeZone = "America/Lima", identity = "certificate-check")
    void checkCertificateExpiry() {
        LocalDate today = fiscalClock.today();
        LocalDate threshold = today.plusDays(30);

        @SuppressWarnings("unchecked")
        List<Tenant> expiring = em.createQuery(
            "SELECT t FROM Tenant t WHERE t.active = true AND t.certificateValidTo IS NOT NULL AND t.certificateValidTo <= :threshold")
            .setParameter("threshold", threshold)
            .getResultList();

        for (Tenant tenant : expiring) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, tenant.getCertificateValidTo());
            if (daysLeft <= 0) {
                LOG.error("CERTIFICATE EXPIRED for " + tenant.getBusinessCode() + " (expired " + tenant.getCertificateValidTo() + ")");
            } else {
                LOG.warn("Certificate expiring in " + daysLeft + " days for " + tenant.getBusinessCode());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Tenant> getActiveTenants() {
        return em.createQuery("SELECT t FROM Tenant t WHERE t.active = true").getResultList();
    }
}
