package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentType;
import io.carabo.core.enums.SendMode;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FiscalRoutingServiceTest {
    private final Instant start=Instant.parse("2026-11-01T00:00:00Z");
    private final Instant end=Instant.parse("2026-12-01T00:00:00Z");
    private FiscalRoutingService.Route route(String flow,String provider,Instant from,Instant to) {return new FiscalRoutingService.Route(flow,provider,from,to);}
    @Test void rejectsOverlappingRoutesForTheSameFlow() {
        var error=assertThrows(WebApplicationException.class,()->FiscalRoutingService.validate(List.of(route("01","SUNAT_DIRECT",start,null),route("01","OSE",end,null))));
        assertEquals(409,error.getResponse().getStatus());
    }
    @Test void acceptsAdjacentIntervalsAndIndependentFlows() {
        assertDoesNotThrow(()->FiscalRoutingService.validate(List.of(route("01","SUNAT_DIRECT",start,end),route("01","OSE",end,null),route("03","SUNAT_DIRECT",start,null))));
        var routes=List.of(route("01","SUNAT_DIRECT",start,end),route("01","OSE",end,null));
        assertEquals("SUNAT_DIRECT",FiscalRoutingService.resolve(routes,"01",start).orElseThrow().provider());
        assertEquals("OSE",FiscalRoutingService.resolve(routes,"01",end).orElseThrow().provider());
        assertTrue(FiscalRoutingService.resolve(routes,"03",start).isEmpty());
    }
    @Test void rejectsUnknownProvidersAndInvalidRanges() {
        for(var invalid:List.of(route("01","UNKNOWN",start,end),route("01","OSE",end,start),route("99","OSE",start,end))) {
            assertEquals(400,assertThrows(WebApplicationException.class,()->FiscalRoutingService.validate(List.of(invalid))).getResponse().getStatus());
        }
    }
    @Test void documentKeepsItsProviderRouteAndCredentialAfterCompanyChanges() {
        var service=new FiscalRoutingService(){@Override public Schedule get(String business){return new Schedule(4,List.of(route("01","OSE",Instant.EPOCH,null)));}};
        service.json=new ObjectMapper().findAndRegisterModules();
        var tenant=new Tenant();tenant.setBusinessCode("B11111111111");tenant.setSendMode(SendMode.SUNAT_DIRECT);tenant.setSolUser("original-user");
        tenant.setOseBaseUrl("https://old.example.invalid");tenant.setOseApiToken("secret://managed/old");
        var doc=new Document();doc.setDocType(DocumentType.fromSunatCode("01"));
        service.capture(doc,tenant,"https://sunat.example.invalid");
        tenant.setOseBaseUrl("https://new.example.invalid");tenant.setOseApiToken("secret://managed/new");tenant.setSolUser("new-user");
        service.capture(doc,tenant,"https://new-sunat.example.invalid");
        var effective=service.effective(doc,tenant);
        assertEquals(SendMode.OSE,effective.getSendMode());assertEquals("https://old.example.invalid",effective.getOseBaseUrl());
        assertEquals("secret://managed/old",effective.getOseApiToken());assertEquals("original-user",effective.getSolUser());
        assertFalse(new ObjectMapper().valueToTree(doc).has("fiscalRouteSnapshot"));
    }
    @Test void plaintextCompatibilityCredentialsAreNeverCopiedIntoDocuments() {
        var service=new FiscalRoutingService(){@Override public Schedule get(String business){return new Schedule(0,List.of());}};
        service.json=new ObjectMapper().findAndRegisterModules();
        var tenant=new Tenant();tenant.setBusinessCode("B11111111111");tenant.setSendMode(SendMode.SUNAT_DIRECT);tenant.setSolPassword("sensitive-fixture");
        var doc=new Document();doc.setDocType(DocumentType.fromSunatCode("01"));
        service.capture(doc,tenant,"https://sunat.example.invalid");
        assertFalse(doc.getFiscalRouteSnapshot().contains("sensitive-fixture"));
    }
}
