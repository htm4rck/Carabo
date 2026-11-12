package io.carabo.application.producer.ports;

import io.carabo.application.producer.ProducerContracts.DocumentActivity;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.producer.Producer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence capabilities bound to the company and transaction of the caller. */
public interface ProducerStore {
    BusinessScope scope();
    Optional<Producer> findForUpdate(UUID id);
    Optional<Producer> findCodeForUpdate(String code);
    Producer save(Producer producer);
    DocumentActivity documents(UUID producerId);
    void recordActivity(UUID producerId, int status, boolean successful, Instant at);
}
