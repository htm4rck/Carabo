package io.carabo.application.producer.ports;

import io.carabo.domain.BusinessScope;
import java.util.function.Function;

public interface ProducerUnitOfWork {
    /** Joins the caller transaction when present; otherwise commits before returning. Failure rolls back all writes. */
    <T> T transaction(BusinessScope scope, Function<ProducerStore, T> operation);
    /** Commits independently of the caller so response activity survives an operation rollback. */
    <T> T independentTransaction(BusinessScope scope, Function<ProducerStore, T> operation);
}
