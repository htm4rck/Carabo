package io.carabo.app.service;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OperationalMetrics {
    @Inject MeterRegistry registry;

    public void message(String result) {
        registry.counter("carabo_async_messages_total", "result", result).increment();
    }

    public void replay() {
        registry.counter("carabo_async_replays_total").increment();
    }

    public void notification(String result) {
        registry.counter("carabo_notifications_total", "result", result).increment();
    }

    public void fiscalResult(String authority, String status) {
        registry.counter("carabo_fiscal_results_total", "authority", authority, "status", status).increment();
    }
}
