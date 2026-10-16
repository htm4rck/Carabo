package io.carabo.app.consumer;

import io.carabo.app.service.DocumentService;
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

    @Incoming("document-emit")
    @Blocking
    public void consume(EmitDocumentRequest request) {
        LOG.info("Received emit request via RabbitMQ: " + request.getDocType()
            + " " + request.getSeries() + " for " + request.getBusinessCode());
        var response = documentService.emit(request);
        if ("VALIDATION_ERROR".equals(response.getStatus())) {
            throw new IllegalArgumentException("Invalid document payload: " + response.getSunatResponse());
        }
    }
}
