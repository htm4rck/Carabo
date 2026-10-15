package io.carabo.app.rest;

import io.carabo.app.client.LayoutClient;
import io.carabo.app.service.BatchService;
import io.carabo.app.service.DocumentQueryService;
import io.carabo.app.service.DocumentService;
import io.carabo.app.security.TenantContext;
import io.carabo.core.dto.EmitDocumentRequest;
import io.carabo.core.dto.EmitDocumentResponse;
import io.carabo.core.entity.Document;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Path("/documents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Documents", description = "Emisión y consulta de documentos electrónicos")
public class DocumentResource {

    @Inject
    DocumentService documentService;

    @Inject
    DocumentQueryService queryService;

    @Inject
    BatchService batchService;

    @Inject
    LayoutClient layoutClient;

    @Inject
    TenantContext tenantContext;

    @POST
    @Path("/emit")
    @Operation(summary = "Emitir documento electrónico", description = "Valida, firma, envía a SUNAT/OSE y retorna resultado")
    @APIResponse(responseCode = "200", description = "Documento procesado")
    public Response emit(EmitDocumentRequest request, @HeaderParam("Idempotency-Key") String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            if (normalizedKey.length() > 100) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Idempotency-Key no puede exceder 100 caracteres").build();
            }
            String externalId = normalizeIdempotencyKey(request.getExternalId());
            if (externalId != null && !externalId.equals(normalizedKey)) {
                return Response.status(Response.Status.CONFLICT)
                    .entity("Idempotency-Key y externalId deben coincidir cuando ambos están presentes").build();
            }
            request.setExternalId(normalizedKey);
        }
        request.setBusinessCode(tenantContext.businessCode());
        EmitDocumentResponse response = documentService.emit(request);
        return Response.ok(response).build();
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Obtener documento por ID")
    public Response getById(@PathParam("id") UUID id) {
        return documentService.findById(id, tenantContext.businessCode())
            .map(doc -> Response.ok(toSummary(doc)).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Operation(summary = "Consultar documentos con filtros", description = "Búsqueda avanzada con paginación")
    public Response query(
        @Parameter(description = "Código de empresa") @QueryParam("businessCode") String businessCode,
        @Parameter(description = "Tipo SUNAT: 01,03,07,08") @QueryParam("docType") String docType,
        @Parameter(description = "Estado del documento") @QueryParam("status") String status,
        @Parameter(description = "Fecha desde (YYYY-MM-DD)") @QueryParam("dateFrom") String dateFrom,
        @Parameter(description = "Fecha hasta (YYYY-MM-DD)") @QueryParam("dateTo") String dateTo,
        @Parameter(description = "Doc. receptor") @QueryParam("receiverDoc") String receiverDoc,
        @Parameter(description = "Serie") @QueryParam("series") String series,
        @Parameter(description = "Texto libre") @QueryParam("search") String search,
        @QueryParam("offset") @DefaultValue("0") int offset,
        @QueryParam("limit") @DefaultValue("50") int limit
    ) {
        var params = new DocumentQueryService.QueryParams();
        params.businessCode = tenantContext.businessCode();
        params.docType = docType != null ? DocumentType.fromSunatCode(docType) : null;
        params.status = status != null ? DocumentStatus.valueOf(status) : null;
        params.dateFrom = dateFrom != null ? LocalDate.parse(dateFrom) : null;
        params.dateTo = dateTo != null ? LocalDate.parse(dateTo) : null;
        params.receiverDoc = receiverDoc;
        params.series = series;
        params.search = search;
        params.offset = offset;
        params.limit = Math.min(limit, 200);

        var result = queryService.query(params);
        return Response.ok(new QueryResponse(
            result.items().stream().map(this::toSummary).toList(),
            result.total(), result.offset(), result.limit()
        )).build();
    }

    @GET
    @Path("/{id}/xml")
    @Produces(MediaType.APPLICATION_XML)
    @Operation(summary = "Descargar XML firmado")
    public Response getXml(@PathParam("id") UUID id) {
        return documentService.getSignedXml(id, tenantContext.businessCode())
            .map(xml -> Response.ok(xml).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Path("/{id}/cdr")
    @Produces(MediaType.APPLICATION_XML)
    @Operation(summary = "Descargar CDR (Constancia de Recepción)")
    public Response getCdr(@PathParam("id") UUID id) {
        return documentService.getCdr(id, tenantContext.businessCode())
            .map(cdr -> Response.ok(cdr).build())
            .orElse(Response.status(404).build());
    }

    @GET
    @Path("/{id}/pdf")
    @Produces("application/pdf")
    @Operation(summary = "Generar PDF representación impresa", description = "Genera PDF A4 o ticket via tordo.layout")
    public Response getPdf(@PathParam("id") UUID id, @Parameter(description = "A4 o TICKET_80MM") @QueryParam("format") String format) {
        return documentService.findById(id, tenantContext.businessCode()).map(doc -> {
            byte[] pdf = layoutClient.generatePdf(doc, format);
            if (pdf == null) return Response.status(502).entity("PDF generation failed").build();
            return Response.ok(pdf)
                .header("Content-Disposition", "inline; filename=\"" + doc.getFullNumber() + ".pdf\"")
                .build();
        }).orElse(Response.status(404).build());
    }

    @POST
    @Path("/{id}/retry")
    @Operation(summary = "Reintentar envío de documento fallido")
    public Response retry(@PathParam("id") UUID id) {
        if (!documentService.retry(id, tenantContext.businessCode())) {
            return Response.status(404).build();
        }
        return documentService.findById(id, tenantContext.businessCode())
            .map(doc -> Response.ok(toSummary(doc)).build())
            .orElse(Response.status(404).build());
    }

    // --- Batch operations ---

    @POST
    @Path("/batch/summary")
    @Operation(summary = "Generar resumen diario de boletas (RC)")
    public Response dailySummary(SummaryRequest request) {
        var result = batchService.sendDailySummary(
            tenantContext.businessCode(),
            request.date != null ? LocalDate.parse(request.date) : LocalDate.now().minusDays(1)
        );
        return Response.ok(result).build();
    }

    @POST
    @Path("/batch/void")
    @Operation(summary = "Enviar comunicación de baja (RA)")
    public Response voidDocuments(VoidRequest request) {
        var result = batchService.sendVoidedCommunication(
            tenantContext.businessCode(), request.documentIds, request.reason);
        return Response.ok(result).build();
    }

    @GET
    @Path("/batch/ticket/{businessCode}/{ticket}")
    @Operation(summary = "Consultar estado de ticket SUNAT")
    public Response checkTicket(@PathParam("businessCode") String businessCode, @PathParam("ticket") String ticket) {
        var result = batchService.checkTicket(tenantContext.businessCode(), ticket);
        return Response.ok(result).build();
    }

    // --- DTOs ---

    private DocumentSummary toSummary(Document doc) {
        var s = new DocumentSummary();
        s.id = doc.getId();
        s.fullNumber = doc.getFullNumber();
        s.docType = doc.getDocType().sunatCode();
        s.docTypeName = doc.getDocType().description();
        s.status = doc.getStatus().name();
        s.total = doc.getTotal();
        s.currency = doc.getCurrency();
        s.receiverName = doc.getReceiverName();
        s.receiverDoc = doc.getReceiverDoc();
        s.issueDate = doc.getIssueDate() != null ? doc.getIssueDate().toString() : null;
        s.hashCode = doc.getHashCode();
        s.externalId = doc.getExternalId();
        s.businessCode = doc.getBusinessCode();
        s.sunatResponse = doc.getSunatResponse();
        s.createdAt = doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null;
        return s;
    }

    public static class DocumentSummary {
        public UUID id;
        public String fullNumber;
        public String docType;
        public String docTypeName;
        public String status;
        public java.math.BigDecimal total;
        public String currency;
        public String receiverName;
        public String receiverDoc;
        public String issueDate;
        public String hashCode;
        public String externalId;
        public String businessCode;
        public String sunatResponse;
        public String createdAt;
    }

    public static class QueryResponse {
        public List<DocumentSummary> items;
        public long total;
        public int offset;
        public int limit;

        public QueryResponse(List<DocumentSummary> items, long total, int offset, int limit) {
            this.items = items;
            this.total = total;
            this.offset = offset;
            this.limit = limit;
        }
    }

    public static class VoidRequest {
        public String businessCode;
        public List<UUID> documentIds;
        public String reason;
    }

    public static class SummaryRequest {
        public String businessCode;
        public String date;
    }
}
