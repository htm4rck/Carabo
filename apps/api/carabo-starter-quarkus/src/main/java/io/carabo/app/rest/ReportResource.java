package io.carabo.app.rest;

import io.carabo.app.service.ExcelExportService;
import io.carabo.app.service.ReportService;
import io.carabo.app.security.TenantContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;

@Path("/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Reports", description = "Reportes tributarios")
public class ReportResource {

    @Inject
    ReportService reportService;

    @Inject
    ExcelExportService excelService;

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
        if (from == null || to == null) {
            return Response.status(400).entity("{\"error\":\"from and to are required\"}").build();
        }
        var report = reportService.salesReport(tenantContext.businessCode(), LocalDate.parse(from), LocalDate.parse(to));
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
        if (year == 0 || month == 0) {
            return Response.status(400).entity("{\"error\":\"year and month are required\"}").build();
        }
        var report = reportService.monthlyReport(tenantContext.businessCode(), year, month);
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
        if (from == null || to == null) {
            return Response.status(400).entity("{\"error\":\"from and to are required\"}").build();
        }
        String authenticatedBusiness = tenantContext.businessCode();
        byte[] xlsx = excelService.exportDocuments(authenticatedBusiness, LocalDate.parse(from), LocalDate.parse(to));
        return Response.ok(xlsx)
            .header("Content-Disposition", "attachment; filename=\"registro-ventas-" + authenticatedBusiness + "-" + from + "-" + to + ".xlsx\"")
            .build();
    }
}
