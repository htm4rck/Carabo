package io.carabo.app.consumer;

import io.carabo.app.service.DocumentService;
import io.carabo.core.dto.EmitDocumentRequest;
import io.smallrye.common.annotation.Identifier;
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
    public void consume(EmitDocumentRequest request) {
        LOG.info("Received emit request via RabbitMQ: " + request.getDocType()
            + " " + request.getSeries() + " for " + request.getBusinessCode());
        try {
            documentService.emit(request);
        } catch (Exception e) {
            LOG.error("Failed to process document emit from queue", e);
        }
    }
}
