package io.carabo.app.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.application.reporting.ReportUseCases;
import io.carabo.application.reporting.ReportContracts.DaySummary;
import io.carabo.application.reporting.ReportContracts.ExportRow;
import io.carabo.application.reporting.ReportContracts.TypeSummary;
import io.carabo.application.reporting.ports.ReportReader;
import io.carabo.app.infrastructure.http.RuleViolationMapper;
import io.carabo.app.security.TenantContext;
import io.carabo.core.entity.Tenant;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.RuleViolation;
import io.carabo.domain.reporting.ReportPeriod;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportResourceTest {
    @Test void preservesTheWireContractAndUsesOnlyTheAuthenticatedCompany() throws Exception {
        var reader = new Reader();
        var resource = resource(reader);
        var response = resource.salesReport("TENANT_B", "2026-01-01", "2026-01-31");
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(response.getEntity());
        assertEquals(200, response.getStatus());
        assertEquals("TENANT_A", json.get("businessCode").asText());
        assertEquals(1, json.get("totalDocuments").asLong());
        assertEquals(new BigDecimal("118.00"), json.get("totalAmount").decimalValue().setScale(2));
        assertEquals("INVOICE", json.get("byType").get(0).get("docType").asText());
        assertEquals(new BusinessScope("TENANT_A"), reader.scope);
        resource.monthlyReport("TENANT_B", 2026, 1);
        assertEquals(new BusinessScope("TENANT_A"), reader.scope);
        var excel = resource.exportExcel("TENANT_B", "2026-01-01", "2026-01-31");
        assertArrayEquals(new byte[] {1, 2}, (byte[]) excel.getEntity());
        assertEquals("attachment; filename=\"registro-ventas-TENANT_A-2026-01-01-2026-01-31.xlsx\"", excel.getHeaderString("Content-Disposition"));
    }

    @Test void invalidDatesKeepTheErrorEnvelopeAndDoNotQueryTheDatabase() throws Exception {
        var reader = new Reader();
        var resource = resource(reader);
        for (String[] period : List.of(new String[] {null, "2026-01-31"}, new String[] {"bad-date", "2026-01-31"},
            new String[] {"2026-02-01", "2026-01-31"})) {
            var error = assertThrows(RuleViolation.class, () -> resource.salesReport("TENANT_B", period[0], period[1]));
            var response = new RuleViolationMapper().toResponse(error);
            assertEquals(400, response.getStatus());
            var json = new ObjectMapper().valueToTree(response.getEntity());
            assertEquals(1, json.size()); assertTrue(json.hasNonNull("error"));
        }
        assertNull(reader.scope);
    }

    private ReportResource resource(Reader reader) {
        var resource = new ReportResource();
        var tenant = new Tenant(); tenant.setBusinessCode("TENANT_A");
        resource.tenantContext = new TenantContext(); resource.tenantContext.authenticateTenant(tenant);
        resource.reports = new ReportUseCases(reader, (scope, period, rows) -> { rows.writeTo(row -> {}); return new byte[] {1, 2}; });
        return resource;
    }
    private static class Reader implements ReportReader {
        BusinessScope scope;
        public List<TypeSummary> byType(BusinessScope scope, ReportPeriod period) {
            this.scope = scope;
            return List.of(new TypeSummary("INVOICE", 1, new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00")));
        }
        public List<DaySummary> byDay(BusinessScope scope, ReportPeriod period) { this.scope = scope; return List.of(); }
        public long scanAccepted(BusinessScope scope, ReportPeriod period, Consumer<ExportRow> sink) { this.scope = scope; return 0; }
    }
}
