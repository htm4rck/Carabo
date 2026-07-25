package io.carabo.app.rest;

import io.carabo.app.client.LayoutClient;
import io.carabo.app.service.DispatchGuideService;
import io.carabo.core.dto.EmitDispatchGuideRequest;
import io.carabo.core.entity.gre.DispatchGuide;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

@Path("/dispatch-guides")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dispatch Guides", description = "Guía de Remisión Electrónica GRE 2.0")
public class DispatchGuideResource {

    @Inject
    DispatchGuideService service;

    @Inject
    LayoutClient layoutClient;

    @POST
    @Path("/emit")
    @Operation(summary = "Emitir guía de remisión electrónica")
    public Response emit(EmitDispatchGuideRequest request) {
        DispatchGuide gre = service.emit(request);
        return Response.status(201).entity(toSummary(gre)).build();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Obtener GRE por ID")
    public Response getById(@PathParam("id") UUID id) {
        return service.findById(id)
            .map(g -> Response.ok(toSummary(g)).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Operation(summary = "Listar GREs por empresa")
    public Response list(
        @QueryParam("businessCode") String businessCode,
        @QueryParam("offset") @DefaultValue("0") int offset,
        @QueryParam("limit") @DefaultValue("50") int limit
    ) {
        if (businessCode == null) return Response.status(400).entity("{\"error\":\"businessCode required\"}").build();
        var guides = service.findByBusiness(businessCode, offset, Math.min(limit, 200));
        return Response.ok(guides.stream().map(this::toSummary).toList()).build();
    }

    @GET
    @Path("/{id}/pdf")
    @Produces("application/pdf")
    public Response getPdf(@PathParam("id") UUID id) {
        // GRE PDF via layout - uses ELECTRONIC_DISPATCH_GUIDE template
        return service.findById(id).map(gre -> {
            // Build minimal Document-like data for layout
            // For now return 501 until GRE template is fully implemented
            return Response.status(501).entity("GRE PDF template pending").build();
        }).orElse(Response.status(404).build());
    }

    private GreSummary toSummary(DispatchGuide g) {
        var s = new GreSummary();
        s.id = g.getId();
        s.fullNumber = g.getFullNumber();
        s.status = g.getStatus().name();
        s.issueDate = g.getIssueDate() != null ? g.getIssueDate().toString() : null;
        s.startDate = g.getStartDate() != null ? g.getStartDate().toString() : null;
        s.receiverName = g.getReceiverName();
        s.receiverDoc = g.getReceiverDoc();
        s.transportMode = g.getTransportMode();
        s.originAddress = g.getOriginAddress();
        s.destinationAddress = g.getDestinationAddress();
        s.transferReasonCode = g.getTransferReasonCode();
        s.businessCode = g.getBusinessCode();
        s.externalId = g.getExternalId();
        s.hashCode = g.getHashCode();
        return s;
    }

    public static class GreSummary {
        public UUID id;
        public String fullNumber;
        public String status;
        public String issueDate;
        public String startDate;
        public String receiverName;
        public String receiverDoc;
        public String transportMode;
        public String originAddress;
        public String destinationAddress;
        public String transferReasonCode;
        public String businessCode;
        public String externalId;
        public String hashCode;
    }
}
