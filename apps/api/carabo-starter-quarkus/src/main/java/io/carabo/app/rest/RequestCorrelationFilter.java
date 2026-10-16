package io.carabo.app.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.util.UUID;

@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class RequestCorrelationFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final String HEADER = "X-Correlation-ID";
    private static final String PROPERTY = RequestCorrelationFilter.class.getName() + ".id";

    @Override
    public void filter(ContainerRequestContext request) {
        String supplied = request.getHeaderString(HEADER);
        String correlationId = supplied != null && supplied.matches("[A-Za-z0-9._:-]{8,100}")
            ? supplied : UUID.randomUUID().toString();
        request.setProperty(PROPERTY, correlationId);
        MDC.put("correlationId", correlationId);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object correlationId = request.getProperty(PROPERTY);
        if (correlationId != null) response.getHeaders().putSingle(HEADER, correlationId.toString());
        MDC.remove("correlationId");
    }
}
