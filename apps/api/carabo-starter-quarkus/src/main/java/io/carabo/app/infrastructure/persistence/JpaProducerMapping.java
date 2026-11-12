package io.carabo.app.infrastructure.persistence;

import io.carabo.core.entity.ProducerIntegration;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.producer.Producer;
import io.carabo.domain.producer.ProducerScopes;

final class JpaProducerMapping {
    private JpaProducerMapping() {}

    static Producer model(ProducerIntegration value) {
        return new Producer(value.getId(), new BusinessScope(value.getBusinessCode()), value.getCode(), value.getName(),
            value.getDescription(), value.getApiKeyHash(), ProducerScopes.parse(value.getScopes()), value.getStatus(),
            value.getCreatedAt(), value.getRotatedAt(), value.getLastUsedAt(), value.getLastSuccessAt(), value.getLastFailureAt(),
            value.getLastHttpStatus(), value.getTotalRequests(), value.getFailureRequests());
    }
}
