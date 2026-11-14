package io.carabo.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.FiscalRouteRevision;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.SendMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.*;

/** Immutable company schedules and the fiscal route retained by each accepted document. */
@ApplicationScoped
public class FiscalRoutingService {
    @Inject EntityManager em;
    @Inject ObjectMapper json;
    public record Route(String flow, String provider, Instant validFrom, Instant validTo) {}
    public record Schedule(int version, List<Route> routes) {}
    public record Save(int expectedVersion, List<Route> routes) {}
    public record Snapshot(int version,String provider,String endpoint,String user,String credential) {}

    public Schedule get(String business) {
        var latest=em.createQuery("FROM FiscalRouteRevision WHERE businessCode=:business ORDER BY version DESC",FiscalRouteRevision.class)
            .setParameter("business",business).setMaxResults(1).getResultStream().findFirst();
        return latest.map(row->new Schedule(row.version,decode(row.routesJson,new TypeReference<List<Route>>(){}))).orElse(new Schedule(0,List.of()));
    }
    @Transactional
    public Schedule save(Tenant tenant,Save input,String actor) {
        if(input==null || input.expectedVersion()<0 || input.routes()==null) throw failure(400,"FISCAL_ROUTE_INVALID","Completa la programación fiscal.");
        validate(input.routes());
        tenant=em.find(Tenant.class,tenant.getId(),LockModeType.PESSIMISTIC_WRITE);
        if(tenant==null) throw failure(404,"FISCAL_COMPANY_NOT_FOUND","La empresa ya no está disponible.");
        var head=get(tenant.getBusinessCode());
        if(head.version()!=input.expectedVersion()) throw failure(409,"FISCAL_ROUTE_VERSION_CONFLICT","La programación cambió. Recarga la versión vigente antes de guardar.");
        // Routes refer only to transports and credentials already configured by an administrator.
        for(var route:input.routes()) {
            String endpoint="OSE".equals(route.provider())?tenant.getOseBaseUrl():tenant.getSunatEndpoint();
            String credential="OSE".equals(route.provider())?tenant.getOseApiToken():tenant.getSolPassword();
            if(endpoint==null || endpoint.isBlank() || credential==null || !credential.startsWith("secret://"))
                throw failure(400,"FISCAL_PROVIDER_NOT_READY","Configura el endpoint y una credencial segura del proveedor en Carabo antes de programarlo.");
        }
        var row=new FiscalRouteRevision(); row.id=UUID.randomUUID();row.businessCode=tenant.getBusinessCode();row.version=head.version()+1;
        row.routesJson=encode(input.routes());row.createdAt=Instant.now();row.actorCode=actor==null?"administrator":actor;
        em.persist(row);em.flush();return new Schedule(row.version,input.routes());
    }
    public static void validate(List<Route> routes) {
        if(routes.size()>100) throw failure(400,"FISCAL_ROUTE_LIMIT","Se permiten hasta 100 intervalos por empresa.");
        for(int i=0;i<routes.size();i++) {
            var a=routes.get(i);
            if(a==null || a.flow()==null || a.provider()==null || !Set.of("01","03","07","08").contains(a.flow()) || !Set.of("SUNAT_DIRECT","OSE").contains(a.provider()) || a.validFrom()==null || (a.validTo()!=null && !a.validTo().isAfter(a.validFrom())))
                throw failure(400,"FISCAL_ROUTE_INVALID","Revisa flujo, proveedor y vigencia de cada ruta.");
            for(int j=0;j<i;j++) { var b=routes.get(j);
                if(a.flow().equals(b.flow()) && (a.validTo()==null || b.validFrom().isBefore(a.validTo())) && (b.validTo()==null || a.validFrom().isBefore(b.validTo())))
                    throw failure(409,"FISCAL_ROUTE_OVERLAP","Dos rutas del mismo flujo no pueden tener vigencias superpuestas.");
            }
        }
    }
    public static Optional<Route> resolve(List<Route> routes,String flow,Instant at) {
        return routes.stream().filter(r->r.flow().equals(flow) && !at.isBefore(r.validFrom()) && (r.validTo()==null || at.isBefore(r.validTo()))).findFirst();
    }
    public void capture(Document doc,Tenant tenant,String defaultEndpoint) {
        if(doc.getFiscalRouteSnapshot()!=null) return;
        var schedule=get(tenant.getBusinessCode());
        var route=resolve(schedule.routes(),doc.getDocType().sunatCode(),Instant.now());
        var mode=route.map(r->SendMode.valueOf(r.provider())).orElse(tenant.getSendMode());
        String credential=mode==SendMode.OSE?tenant.getOseApiToken():tenant.getSolPassword();
        // Legacy plaintext is never duplicated into a document. Managed references are immutable.
        String reference=credential!=null && credential.startsWith("secret://")?credential:null;
        doc.setSendMode(mode);
        doc.setFiscalRouteSnapshot(encode(new Snapshot(schedule.version(),mode.name(),mode==SendMode.OSE?tenant.getOseBaseUrl():defaultEndpoint,tenant.getSolUser(),reference)));
    }
    public Tenant effective(Document doc,Tenant current) {
        Snapshot snapshot=doc.getFiscalRouteSnapshot()==null?null:decode(doc.getFiscalRouteSnapshot(),new TypeReference<Snapshot>(){});
        var result=new Tenant(); result.setBusinessCode(current.getBusinessCode());result.setEnvironment(current.getEnvironment());result.setActive(current.isActive());
        result.setCertificateValidFrom(current.getCertificateValidFrom());result.setCertificateValidTo(current.getCertificateValidTo());
        result.setSendMode(snapshot==null?doc.getSendMode():SendMode.valueOf(snapshot.provider()));
        result.setSunatEndpoint(snapshot!=null && result.getSendMode()!=SendMode.OSE?snapshot.endpoint():current.getSunatEndpoint());
        result.setOseBaseUrl(snapshot!=null && result.getSendMode()==SendMode.OSE?snapshot.endpoint():current.getOseBaseUrl());
        result.setSolUser(snapshot==null?current.getSolUser():snapshot.user());
        result.setSolPassword(snapshot!=null && snapshot.credential()!=null && result.getSendMode()!=SendMode.OSE?snapshot.credential():current.getSolPassword());
        result.setOseApiToken(snapshot!=null && snapshot.credential()!=null && result.getSendMode()==SendMode.OSE?snapshot.credential():current.getOseApiToken());
        result.setCertificateFile(current.getCertificateFile());result.setCertificatePassword(current.getCertificatePassword());result.setCertificateAlias(current.getCertificateAlias());
        result.setIssuerRuc(current.getIssuerRuc());result.setIssuerName(current.getIssuerName());result.setIssuerAddress(current.getIssuerAddress());result.setIssuerUbigeo(current.getIssuerUbigeo());
        return result;
    }
    private String encode(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("No se pudo conservar la ruta fiscal",e);} }
    private <T> T decode(String value,TypeReference<T> type) { try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("La ruta fiscal guardada no es válida",e);} }
    private static WebApplicationException failure(int status,String code,String message) { return new WebApplicationException(Response.status(status).entity(Map.of("errorCode",code,"message",message)).build()); }
}
