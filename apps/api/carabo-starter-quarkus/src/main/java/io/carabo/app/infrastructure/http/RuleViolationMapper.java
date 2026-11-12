package io.carabo.app.infrastructure.http;

import io.carabo.domain.RuleViolation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class RuleViolationMapper implements ExceptionMapper<RuleViolation> {
    @Override
    public Response toResponse(RuleViolation error) {
        int status = switch (error.kind()) {
            case INVALID -> 400;
            case MISSING -> 404;
            case CONFLICT -> 409;
        };
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new ErrorResponse(error.getMessage())).build();
    }

    public record ErrorResponse(String error) {}
}
