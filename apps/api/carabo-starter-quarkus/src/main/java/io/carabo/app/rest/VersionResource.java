package io.carabo.app.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource {
    @ConfigProperty(name = "carabo.build.version") String version;
    @ConfigProperty(name = "carabo.build.date") String buildDate;
    @ConfigProperty(name = "carabo.build.commit") String commit;

    @GET
    public BuildInfo get() { return new BuildInfo(version, buildDate, commit); }

    public record BuildInfo(String version, String buildDate, String commit) {}
}
