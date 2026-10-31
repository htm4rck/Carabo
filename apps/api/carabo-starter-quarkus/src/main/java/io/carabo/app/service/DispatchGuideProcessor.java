package io.carabo.app.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class DispatchGuideProcessor {
    @Inject DispatchGuideService service;
    public void process(UUID id, String businessCode) {
        var submission = service.claimSubmission(id, businessCode);
        if (submission != null) service.recordSubmission(submission, service.submit(submission));
    }
}
