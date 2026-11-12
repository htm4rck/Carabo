package io.carabo.app.rest;

import io.carabo.application.reporting.ReportUseCases;
import io.carabo.app.security.TenantContext;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.RuleViolation;
import io.carabo.domain.reporting.ReportPeriod;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.time.DateTimeException;

@Path("/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reports", description = "Reportes tributarios")
public class ReportResource {

    @Inject
    ReportUseCases reports;

    @Inject
    TenantContext tenantContext;

    @GET
    @Path("/sales")
    @Operation(summary = "Reporte de ventas por periodo", description = "Agrupa documentos aceptados por tipo con totales de base, IGV y monto")
    public Response salesReport(
        @QueryParam("businessCode") String businessCode,
        @QueryParam("from") String from,
        @QueryParam("to") String to
    ) {
        var report = reports.sales(new BusinessScope(tenantContext.businessCode()), period(from, to));
        return Response.ok(report).build();
    }

    @GET
    @Path("/monthly")
    @Operation(summary = "Reporte mensual", description = "Desglose diario de documentos emitidos en un mes")
    public Response monthlyReport(
        @QueryParam("businessCode") String businessCode,
        @QueryParam("year") int year,
        @QueryParam("month") int month
    ) {
        var report = reports.monthly(new BusinessScope(tenantContext.businessCode()), year, month);
        return Response.ok(report).build();
    }

    @GET
    @Path("/export/excel")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Exportar registro de ventas a Excel", description = "Genera XLSX con documentos aceptados del periodo")
    public Response exportExcel(
        @QueryParam("businessCode") String businessCode,
        @QueryParam("from") String from,
        @QueryParam("to") String to
    ) {
        String authenticatedBusiness = tenantContext.businessCode();
        byte[] xlsx = reports.export(new BusinessScope(authenticatedBusiness), period(from, to));
        return Response.ok(xlsx)
            .header("Content-Disposition", "attachment; filename=\"registro-ventas-" + authenticatedBusiness + "-" + from + "-" + to + ".xlsx\"")
            .build();
    }

    private ReportPeriod period(String from, String to) {
        if (from == null || to == null) {
            throw new RuleViolation("INVALID_REPORT_PERIOD", "from and to are required", RuleViolation.Kind.INVALID);
        }
        try {
            return new ReportPeriod(LocalDate.parse(from), LocalDate.parse(to));
        } catch (DateTimeException error) {
            throw new RuleViolation("INVALID_REPORT_PERIOD", "from and to must be valid ISO dates", RuleViolation.Kind.INVALID);
        }
    }
}
