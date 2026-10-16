package io.carabo.app.service;

import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class FiscalStatusService {
    @Inject EntityManager em;
    @Inject FiscalClock fiscalClock;
    @Inject FiscalReadinessService readinessService;

    public FiscalStatus assess(Tenant tenant) {
        LocalDate today = fiscalClock.today();
        Long days = tenant.getCertificateValidTo() == null ? null
            : ChronoUnit.DAYS.between(today, tenant.getCertificateValidTo());
        String certificateState = certificateState(tenant, today, days);
        Instant lastSignedAt = maxInstant(
            "SELECT MAX(d.updatedAt) FROM Document d WHERE d.businessCode = :bc AND d.hashCode IS NOT NULL", tenant.getBusinessCode());
        Instant lastAcceptedAt = em.createQuery(
                "SELECT MAX(d.updatedAt) FROM Document d WHERE d.businessCode = :bc AND d.status IN (:accepted, :observed)", Instant.class)
            .setParameter("bc", tenant.getBusinessCode())
            .setParameter("accepted", DocumentStatus.ACCEPTED)
            .setParameter("observed", DocumentStatus.OBSERVED)
            .getSingleResult();
        var readiness = readinessService.assess(tenant);
        return new FiscalStatus(tenant.getBusinessCode(), tenant.getBusinessName(), tenant.getEnvironment(),
            tenant.getSendMode() != null ? tenant.getSendMode().name() : null,
            readiness.ready(), readiness.blockers(), readiness.checks(),
            new CertificateStatus(certificateState, tenant.getCertificateAlias(),
                tenant.getCertificateValidFrom(), tenant.getCertificateValidTo(), days,
                tenant.getCertificateFile() != null && !tenant.getCertificateFile().isBlank(),
                "LOCAL_PREFLIGHT", "La cadena de confianza, revocación e interoperabilidad se confirman con homologación externa."),
            lastSignedAt, lastAcceptedAt, Instant.now());
    }

    private Instant maxInstant(String jpql, String businessCode) {
        return em.createQuery(jpql, Instant.class).setParameter("bc", businessCode).getSingleResult();
    }

    private String certificateState(Tenant tenant, LocalDate today, Long days) {
        if (tenant.getCertificateValidFrom() == null || tenant.getCertificateValidTo() == null) return "MISSING";
        if (tenant.getCertificateValidFrom().isAfter(today) || days == null || days < 0) return "EXPIRED";
        if (days <= 15) return "CRITICAL";
        if (days <= 60) return "EXPIRING";
        return "VALID";
    }

    public record FiscalStatus(String businessCode, String businessName, String environment, String sendMode,
                               boolean ready, long blockers, java.util.List<FiscalReadinessService.ReadinessCheck> checks,
                               CertificateStatus certificate, Instant lastSignedAt, Instant lastAcceptedAt, Instant checkedAt) {}
    public record CertificateStatus(String state, String alias, LocalDate validFrom, LocalDate validTo,
                                    Long daysRemaining, boolean stored, String verificationLevel, String limitation) {}
}
