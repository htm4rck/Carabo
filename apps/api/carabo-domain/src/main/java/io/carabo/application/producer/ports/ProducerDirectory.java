package io.carabo.application.producer.ports;

import io.carabo.application.producer.ProducerContracts.DocumentActivity;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.producer.Producer;
import java.util.List;
import java.util.Optional;

public interface ProducerDirectory {
    List<Overview> list(BusinessScope scope);
    Optional<Producer> activeByCredentialHash(String hash);

    record Overview(Producer producer, DocumentActivity documents) {}
}
