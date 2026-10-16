package io.carabo.app.consumer;

import io.carabo.app.service.DocumentService;
import io.carabo.app.service.AsyncMessageService;
import io.carabo.app.service.OperationalMetrics;
import io.carabo.core.dto.EmitDocumentRequest;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DocumentConsumer {

    private static final Logger LOG = Logger.getLogger(DocumentConsumer.class);

    @Inject
    DocumentService documentService;

    @Inject AsyncMessageService asyncMessageService;
    @Inject OperationalMetrics metrics;

    @Incoming("document-emit")
    @Blocking
    public void consume(EmitDocumentRequest request) {
        var auditId = asyncMessageService.received(request);
        LOG.info("Received emit request via RabbitMQ: " + request.getDocType()
            + " " + request.getSeries() + " for " + request.getBusinessCode());
        try {
            var response = documentService.emit(request);
            if ("VALIDATION_ERROR".equals(response.getStatus())) {
                throw new IllegalArgumentException("Invalid document payload: " + response.getSunatResponse());
            }
            asyncMessageService.finish(auditId, "PROCESSED", null);
            metrics.message("processed");
        } catch (RuntimeException error) {
            asyncMessageService.finish(auditId, "DEAD", safeMessage(error));
            metrics.message("dead");
            throw error;
        }
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName()
            : message.substring(0, Math.min(message.length(), 2000));
    }
}
