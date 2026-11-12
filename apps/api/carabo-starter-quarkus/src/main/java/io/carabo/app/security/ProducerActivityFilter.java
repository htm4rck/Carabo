package io.carabo.app.security;

import io.carabo.application.producer.ProducerUseCases;
import io.carabo.domain.BusinessScope;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Persists safe per-producer activity after the response status is known. */
@Provider
public class ProducerActivityFilter implements ContainerResponseFilter {
    @Inject TenantContext tenantContext;
    @Inject ProducerUseCases producers;

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (tenantContext.producerId() != null) {
            producers.recordActivity(new BusinessScope(tenantContext.businessCode()), tenantContext.producerId(), response.getStatus());
        }
    }
}
