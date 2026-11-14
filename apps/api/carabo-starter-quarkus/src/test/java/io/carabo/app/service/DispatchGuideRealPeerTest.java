package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.carabo.app.client.LayoutClient;
import io.carabo.app.infrastructure.persistence.JpaProducerRepository;
import io.carabo.app.rest.DispatchGuideResource;
import io.carabo.app.security.*;
import io.carabo.application.producer.ProducerUseCases;
import io.carabo.core.dto.EmitDispatchGuideRequest;
import io.carabo.core.entity.*;
import io.carabo.core.entity.gre.*;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.ubl.gre.GreUblBuilder;
import io.carabo.signer.XmlSigner;
import io.carabo.signer.XmlSignatureVerifier;
import io.carabo.sunat.gre.SunatGreClient;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import java.lang.reflect.Proxy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Real Carabo HTTP contract/producer auth/JPA/UBL/signature/storage. Fiscal network and PDF renderer are local test dependencies. */
@EnabledIfEnvironmentVariable(named="CARABO_GUIDE_E2E_READY_FILE",matches=".+")
class DispatchGuideRealPeerTest extends DispatchGuidePersistenceTest {
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final String apiKey=UUID.randomUUID().toString()+UUID.randomUUID(),layoutToken=UUID.randomUUID().toString();
    Path certificate;final AtomicInteger fiscalSubmissions=new AtomicInteger(),pdfRequests=new AtomicInteger(),authDenials=new AtomicInteger();
    @BeforeAll @Override void database() throws Exception {
        super.database();factory.close();
        factory=new Configuration().addAnnotatedClass(Tenant.class).addAnnotatedClass(DispatchGuide.class).addAnnotatedClass(DispatchGuideItem.class).addAnnotatedClass(ProducerIntegration.class)
            .setProperty("hibernate.connection.url",url).setProperty("hibernate.connection.username","postgres").setProperty("hibernate.connection.password",password)
            .setProperty("hibernate.connection.driver_class","org.postgresql.Driver").setProperty("hibernate.connection.pool_size","8").setProperty("hibernate.hbm2ddl.auto","validate").buildSessionFactory();
        certificate=storage.resolve("laboratory-only.p12");
        var keytool=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool").toString(),"-genkeypair","-alias","signing","-keyalg","RSA","-keysize","2048","-dname","CN=Carabo Lab,O=Tordo,C=PE","-storetype","PKCS12","-keystore",certificate.toString(),"-storepass","laboratory-only","-keypass","laboratory-only","-validity","365","-noprompt").redirectErrorStream(true).start();
        keytool.getInputStream().readAllBytes();assertEquals(0,keytool.waitFor());
    }
    @Override DispatchGuideService service(EntityManager em) {
        var result=new DispatchGuideService(){
            @Override protected XmlSigner.SignResult signGuide(DispatchGuide guide,Tenant tenant){try(var input=Files.newInputStream(certificate)){var signed=new XmlSigner(input,"laboratory-only").sign(new GreUblBuilder().build(guide));assertTrue(XmlSignatureVerifier.verify(signed.signedXml()).valid());return signed;}catch(Exception error){throw new IllegalStateException(error);}}
            @Override protected SunatGreClient greClient(Tenant tenant){return new SunatGreClient("local","local","local","local",true){
                @Override public GreSendResult send(String filename,String xml){assertTrue(xml.contains("SignatureValue"));fiscalSubmissions.incrementAndGet();return new GreSendResult(true,"local-fiscal-ticket",null,false,false);}
                @Override public GreStatusResult checkStatus(String ticket){return remote;}
            };}
        };
        var base=super.service(em);result.em=em;result.objectMapper=mapper;result.fiscalClock=clock;result.greClientId=Optional.empty();result.greClientSecret=Optional.empty();result.secretResolver=new SecretResolver();result.storagePath=storage.toString();result.storageEncryptionKey=Optional.empty();result.tenantService=base.tenantService;result.correlativeService=base.correlativeService;result.idempotencyService=base.idempotencyService;return result;
    }
    private TenantContext authenticate(EntityManager em,com.sun.net.httpserver.HttpExchange exchange){try{
        var context=new TenantContext();var tenants=new TenantService();tenants.em=em;var repository=new JpaProducerRepository();inject(repository,"em",em);
        var filter=new ApiKeyFilter();inject(filter,"tenantService",tenants);inject(filter,"producers",new ProducerUseCases(repository,repository,new ApiKeyService(),Clock.systemUTC()));inject(filter,"tenantContext",context);inject(filter,"secretResolver",new SecretResolver());
        var rejected=new AtomicReference<Response>();var uri=(UriInfo)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{UriInfo.class},(p,m,a)->m.getName().equals("getPath")?exchange.getRequestURI().getPath().substring(1):null);
        var request=(ContainerRequestContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ContainerRequestContext.class},(p,m,a)->switch(m.getName()){case "getUriInfo"->uri;case "getHeaderString"->exchange.getRequestHeaders().getFirst(a[0].toString());case "getMethod"->exchange.getRequestMethod();case "abortWith"->{rejected.set((Response)a[0]);yield null;}default->null;});
        filter.filter(request);if(rejected.get()!=null){authDenials.incrementAndGet();throw new jakarta.ws.rs.WebApplicationException(rejected.get());}return context;
    }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}catch(Exception e){if(e instanceof RuntimeException r)throw r;throw new IllegalStateException(e);}}
    private DispatchGuideResource resource(EntityManager em,TenantContext context,LayoutClient layout){try{
        var resource=new DispatchGuideResource();inject(resource,"service",service(em));inject(resource,"tenantContext",context);inject(resource,"layoutClient",layout);inject(resource,"payloadValidator",new DispatchGuidePayloadValidator());inject(resource,"fiscalClock",clock);inject(resource,"objectMapper",mapper);inject(resource,"secrets",new SecretResolver());inject(resource,"layoutServiceToken",Optional.of(layoutToken));return resource;
    }catch(Exception e){throw new IllegalStateException(e);}}
    @Test void servesRealComponentsForSterniaWithoutExternalFiscalNetwork() throws Exception {
        remote=new SunatGreClient.GreStatusResult(true,"0","Laboratory accepted fixture; no SUNAT network","<ApplicationResponse><ResponseCode>0</ResponseCode></ApplicationResponse>",false,false);
        fiscalSubmissions.set(0);pdfRequests.set(0);authDenials.set(0);
        configure();tx(em->{var identity=new ProducerIntegration();identity.setBusinessCode(business);identity.setCode("sternia-laboratory");identity.setName("Local test only");identity.setApiKeyHash(new ApiKeyService().hash(apiKey));identity.setScopes("DOCUMENT_EMIT,DOCUMENT_READ");em.persist(identity);return null;});
        var renderer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);renderer.createContext("/pdf/documents/generate",exchange->{try{
            assertEquals(layoutToken,exchange.getRequestHeaders().getFirst("x-tordo-service-token"));assertEquals(business,exchange.getRequestHeaders().getFirst("business-code"));var payload=mapper.readTree(exchange.getRequestBody());assertEquals("ELECTRONIC_DISPATCH_GUIDE",payload.path("templateType").asText());assertEquals("20123456789",payload.path("data").path("issuer").path("ruc").asText());pdfRequests.incrementAndGet();var bytes="%PDF-1.7\nLocal renderer fixture".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/pdf");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
        }finally{exchange.close();}});renderer.start();
        var layout=new LayoutClient();inject(layout,"layoutBaseUrl","http://127.0.0.1:"+renderer.getAddress().getPort());inject(layout,"objectMapper",mapper);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{try{
            if(exchange.getRequestURI().getPath().equals("/__qa/stats")){var bytes=mapper.writeValueAsBytes(Map.of("fiscalSubmissions",fiscalSubmissions.get(),"pdfRequests",pdfRequests.get(),"authDenials",authDenials.get(),"guides",tx(em->((Number)em.createNativeQuery("SELECT count(*) FROM carabo.dispatch_guide").getSingleResult()).longValue())));exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);return;}
            byte[] body=exchange.getRequestBody().readAllBytes();var response=tx(em->{var context=authenticate(em,exchange);var resource=resource(em,context,layout);String path=exchange.getRequestURI().getPath();
                if(path.equals("/dispatch-guides/readiness"))return resource.readiness();
                if(path.equals("/dispatch-guides/emit")){try{return resource.emit(mapper.readValue(body,EmitDispatchGuideRequest.class),exchange.getRequestHeaders().getFirst("Idempotency-Key"));}catch(Exception e){throw new IllegalStateException(e);}}
                String[] parts=path.split("/");if(parts.length<3)return Response.status(404).build();UUID id=UUID.fromString(parts[2]);return parts.length==3?resource.getById(id):switch(parts[3]){case "xml"->resource.getXml(id);case "cdr"->resource.getCdr(id);case "pdf"->resource.getPdf(id,null);default->Response.status(404).build();};
            });
            if(exchange.getRequestURI().getPath().equals("/dispatch-guides/emit")&&response.getStatus()==201){var summary=mapper.valueToTree(response.getEntity());UUID id=UUID.fromString(summary.path("id").asText());var submission=tx(em->service(em).claimSubmission(id,business));if(submission!=null){var sent=tx(em->service(em).submit(submission));tx(em->{service(em).recordSubmission(submission,sent);return null;});tx(em->service(em).checkStatus(id,business));}}
            byte[] bytes=response.getEntity()==null?new byte[0]:response.getEntity() instanceof byte[] b?b:response.getEntity() instanceof String s?s.getBytes(StandardCharsets.UTF_8):mapper.writeValueAsBytes(response.getEntity());exchange.getResponseHeaders().set("Content-Type",response.getMediaType()==null?"application/json":response.getMediaType().toString());exchange.sendResponseHeaders(response.getStatus(),bytes.length==0?-1:bytes.length);if(bytes.length>0)exchange.getResponseBody().write(bytes);
        }catch(jakarta.ws.rs.WebApplicationException denied){exchange.sendResponseHeaders(denied.getResponse().getStatus(),-1);}catch(Throwable error){System.err.println("Local Carabo peer failed: "+error.getClass().getSimpleName()+": "+error.getMessage());exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}});server.start();
        Path ready=Path.of(System.getenv("CARABO_GUIDE_E2E_READY_FILE")),stop=Path.of(System.getenv("CARABO_GUIDE_E2E_STOP_FILE"));
        try{Files.writeString(ready,mapper.writeValueAsString(Map.of("url","http://127.0.0.1:"+server.getAddress().getPort(),"apiKey",apiKey)));long deadline=System.nanoTime()+java.time.Duration.ofMinutes(3).toNanos();while(!Files.exists(stop)&&System.nanoTime()<deadline)Thread.sleep(100);assertTrue(Files.exists(stop),"Sternia laboratory client did not finish");assertEquals(1,fiscalSubmissions.get());assertTrue(pdfRequests.get()>0);assertTrue(authDenials.get()>0);assertEquals(1L,tx(em->em.createQuery("SELECT count(g) FROM DispatchGuide g",Long.class).getSingleResult()).longValue());}
        finally{server.stop(0);renderer.stop(0);Files.deleteIfExists(ready);}
    }
}
