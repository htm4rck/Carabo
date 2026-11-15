package io.carabo.app.service;

import io.carabo.app.security.SecretResolver;
import io.carabo.core.enums.SendMode;
import io.carabo.ose.OseClient;
import io.carabo.sunat.SunatBillService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class BatchTicketService {
    @Inject BatchTicketStore store;
    @Inject SecretResolver secrets;

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public BatchService.BatchResult check(String businessCode, String ticket) {
        var snapshot = store.prepare(businessCode, ticket);
        if (snapshot.terminal() != null) return snapshot.terminal();
        return store.complete(snapshot, query(snapshot));
    }

    protected BatchTicketStore.Outcome query(BatchTicketStore.Snapshot snapshot) {
        if (snapshot.mode() == SendMode.OSE) {
            var result = new OseClient(snapshot.oseUrl(), secrets.resolve(snapshot.oseTokenRef())).getStatus(snapshot.ticket());
            return new BatchTicketStore.Outcome(result.accepted(), state(result.processing(), result.accepted(), result.observed(), result.retryable(), result.cdrXml()), result.responseMsg(), result.cdrXml());
        }
        var result = new SunatBillService(snapshot.endpoint(), snapshot.solUser(), secrets.resolve(snapshot.passwordRef())).getStatus(snapshot.ticket());
        return new BatchTicketStore.Outcome(result.accepted(), state(result.processing(), result.accepted(), result.observed(), result.retryable(), result.cdrXml()), result.responseMsg(), result.cdrXml());
    }

    private String state(boolean processing, boolean accepted, boolean observed, boolean retryable, String cdr) {
        if (processing) return "PENDING";
        if (accepted) return observed ? "OBSERVED" : "ACCEPTED";
        return !retryable && cdr != null && !cdr.isBlank() ? "REJECTED" : "ERROR";
    }
}
