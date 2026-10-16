package io.carabo.app.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Makes the time contract explicit: audit timestamps are UTC instants while
 * fiscal dates are resolved in the configured business timezone.
 */
@ApplicationScoped
public class FiscalClock {

    private final Clock clock;
    private final ZoneId fiscalZone;

    public FiscalClock(
        @ConfigProperty(name = "carabo.time.fiscal-zone", defaultValue = "America/Lima") String fiscalZone
    ) {
        this.clock = Clock.systemUTC();
        this.fiscalZone = ZoneId.of(fiscalZone);
    }

    public Instant nowUtc() {
        return clock.instant();
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(fiscalZone));
    }

    public String zoneId() {
        return fiscalZone.getId();
    }
}
