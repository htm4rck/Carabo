package io.carabo.app.security;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/** Company discovery before selecting a tenant; the JWT subject owns the scope. */
@Path("/access/businesses")
@Produces(MediaType.APPLICATION_JSON)
public class AccessResource {
    @Inject HumanAccessService humanAccessService;
    @Inject JsonWebToken jwt;

    @GET
    public List<HumanAccessService.AvailableBusiness> businesses() {
        return humanAccessService.availableBusinesses(jwt.getSubject(),
            ApiKeyFilter.isRootLicense(jwt.getClaim("licenseType")));
    }
}
