package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.app.security.SecretResolver;
import io.carabo.core.dto.EmitDispatchGuideRequest;
import io.carabo.core.entity.Tenant;
import io.carabo.core.entity.gre.DispatchGuide;
import io.carabo.core.entity.gre.DispatchGuideItem;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.signer.XmlSigner;
import io.carabo.sunat.gre.SunatGreClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable PostgreSQL, real migrations/locks/storage; no SUNAT requests or notifications. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DispatchGuidePersistenceTest {
    String container, password, url;
    EntityManagerFactory factory;
    Path storage;
    final String business = "B20123456789";
    final SunatGreClient.GreStatusResult rejection = new SunatGreClient.GreStatusResult(false,"2335","Documento rechazado", "<CDR><ResponseCode>2335</ResponseCode></CDR>",true,false);
    SunatGreClient.GreStatusResult remote;
    final FiscalClock clock = new FiscalClock("America/Lima");

    @BeforeAll void database() throws Exception {
        container = "carabo-gre-test-" + UUID.randomUUID().toString().substring(0,8); password = UUID.randomUUID().toString();
        run("docker","run","--rm","-d","--name",container,"-e","POSTGRES_PASSWORD="+password,"-e","POSTGRES_DB=gre_tests","-p","127.0.0.1::5432","postgres:16-alpine");
        String mapped = run("docker","port",container,"5432/tcp").trim();
        url = "jdbc:postgresql://127.0.0.1:" + mapped.substring(mapped.lastIndexOf(':')+1) + "/gre_tests";
        Exception last = null;
        for (int attempt=0; attempt<50; attempt++) {
            try (var connection=DriverManager.getConnection(url,"postgres",password)) { last=null; break; }
            catch (Exception error) { last=error; Thread.sleep(100); }
        }
        if(last!=null)throw last;
        Path root=Path.of("").toAbsolutePath();
        while(root!=null&&!Files.exists(root.resolve("migrations/014-deferred-dispatch-guides.sql")))root=root.getParent();
        assertNotNull(root);
        try(var connection=DriverManager.getConnection(url,"postgres",password);var statement=connection.createStatement()) {
            for(Path file:Files.list(root.resolve("migrations")).filter(p->p.getFileName().toString().endsWith(".sql")&&!p.getFileName().toString().startsWith("002-")).sorted().toList()) statement.execute(Files.readString(file));
        }
        factory=new Configuration().addAnnotatedClass(Tenant.class).addAnnotatedClass(DispatchGuide.class).addAnnotatedClass(DispatchGuideItem.class)
            .setProperty("hibernate.connection.url",url).setProperty("hibernate.connection.username","postgres").setProperty("hibernate.connection.password",password)
            .setProperty("hibernate.connection.driver_class","org.postgresql.Driver").setProperty("hibernate.connection.pool_size","8")
            .setProperty("hibernate.hbm2ddl.auto","validate").buildSessionFactory();
        storage=Files.createTempDirectory("carabo-gre-test-");
    }
    @AfterAll void cleanup() throws Exception { if(factory!=null)factory.close();if(container!=null)run("docker","rm","-f",container); }
    @BeforeEach void reset() {
        remote=new SunatGreClient.GreStatusResult(false,"98","En proceso",null,false,false);
        tx(em->{em.createNativeQuery("TRUNCATE carabo.dispatch_guide_item,carabo.dispatch_guide,carabo.document_sequence,carabo.tenant CASCADE").executeUpdate();
            var tenant=new Tenant();tenant.setBusinessCode(business);tenant.setIssuerRuc("20123456789");tenant.setIssuerName("Empresa de prueba");tenant.setEnvironment("PRODUCTION");tenant.setSolUser("MODDATOS");tenant.setSolPassword("test-only");em.persist(tenant);return null;});
    }
    DispatchGuideService service(EntityManager em) {
        var service=new DispatchGuideService(){
            @Override protected XmlSigner.SignResult signGuide(DispatchGuide guide,Tenant tenant){return new XmlSigner.SignResult("<SignedGuide/>","test-digest");}
            @Override protected SunatGreClient greClient(Tenant tenant){return new SunatGreClient("test","test","test","test",true){@Override public GreStatusResult checkStatus(String ticket){return remote;}};}
        };
        service.em=em;service.objectMapper=new ObjectMapper().findAndRegisterModules();service.fiscalClock=clock;
        service.greClientId=Optional.empty();service.greClientSecret=Optional.empty();service.secretResolver=new SecretResolver();
        service.storagePath=storage.toString();service.storageEncryptionKey=Optional.empty();
        var tenants=new TenantService();tenants.em=em;service.tenantService=tenants;
        var correlatives=new CorrelativeService();correlatives.em=em;service.correlativeService=correlatives;
        var idempotency=new IdempotencyService();idempotency.em=em;service.idempotencyService=idempotency;
        return service;
    }
    <T>T tx(Function<EntityManager,T> body){try(var em=factory.createEntityManager()){var tx=em.getTransaction();tx.begin();try{T result=body.apply(em);tx.commit();return result;}catch(RuntimeException error){tx.rollback();throw error;}}}
    EmitDispatchGuideRequest request(){
        var request=new EmitDispatchGuideRequest();request.setBusinessCode(business);request.setExternalId("birdport:delivery:test");request.setSeries("T001");request.setIssueDate(clock.today());request.setStartDate(clock.today());request.setTransportMode("02");
        var receiver=new EmitDispatchGuideRequest.Receiver();receiver.setDocType("1");receiver.setDoc("87654321");receiver.setName("Receptor de prueba");request.setReceiver(receiver);
        var item=new EmitDispatchGuideRequest.Item();item.setProductCode("P1");item.setProductName("Producto de prueba");item.setQuantity(java.math.BigDecimal.ONE);request.setItems(List.of(item));return request;
    }
    DispatchGuide read(UUID id){return tx(em->em.find(DispatchGuide.class,id));}
    void configure(){tx(em->{var tenant=service(em).tenantService.resolve(business);tenant.setGreClientId("test-client");tenant.setGreClientSecret("test-only-secret");return null;});}
    @Test void storesBlockedRequestsWithoutCallingSunatOrConsumingANumberAndDeduplicatesConcurrentRetries(){
        var request=request();var first=CompletableFuture.supplyAsync(()->tx(em->service(em).emit(request)));
        var second=CompletableFuture.supplyAsync(()->tx(em->service(em).emit(request)));
        var guide=first.join();assertEquals(guide.getId(),second.join().getId());assertEquals(DocumentStatus.BLOCKED_CONFIGURATION,guide.getStatus());assertNull(guide.getNumber());
        assertEquals(0L,((Number)tx(em->em.createNativeQuery("SELECT count(*) FROM carabo.document_sequence").getSingleResult())).longValue());
        var changed=request();changed.getItems().getFirst().setQuantity(java.math.BigDecimal.TEN);
        assertThrows(jakarta.ws.rs.ClientErrorException.class,()->tx(em->service(em).emit(changed)));
    }
    @Test void persistsTheSubmissionClaimBeforeExternalWorkAndDoesNotResendAfterACrash(){
        var guide=tx(em->service(em).emit(request()));configure();
        var submission=tx(em->service(em).claimSubmission(guide.getId(),business));assertNotNull(submission);
        assertEquals(DocumentStatus.SIGNED,read(guide.getId()).getStatus());
        assertNull(tx(em->service(em).claimSubmission(guide.getId(),business)));
        assertTrue(tx(em->service(em).findSubmissionsDue(20)).isEmpty());
        assertTrue(tx(em->service(em).getSignedXml(guide.getId(),business)).isPresent());
    }
    @Test void rollsBackGuideAndIdempotencyChangesTogether(){
        assertThrows(IllegalStateException.class,()->tx(em->{service(em).emit(request());throw new IllegalStateException("rollback");}));
        assertEquals(0L,tx(em->em.createQuery("SELECT count(g) FROM DispatchGuide g",Long.class).getSingleResult()).longValue());
        assertNotNull(tx(em->service(em).emit(request())).getId());
    }
    @Test void laterRejectionIsTerminalAndPreservesTheCdrAndActualReason(){
        var guide=sent();remote=rejection;
        tx(em->service(em).checkStatus(guide.getId(),business));
        var rejected=read(guide.getId());assertEquals(DocumentStatus.REJECTED,rejected.getStatus());assertEquals("2335",rejected.getSunatResponseCode());assertNull(rejected.getNextRetryAt());
        assertTrue(tx(em->service(em).getCdr(guide.getId(),business)).orElseThrow().contains("2335"));
        remote=new SunatGreClient.GreStatusResult(true,"0","Aceptada","<CDR/>",false,false);
        tx(em->service(em).checkStatus(guide.getId(),business));assertEquals(DocumentStatus.REJECTED,read(guide.getId()).getStatus());
        assertTrue(tx(em->service(em).findTicketsDue(20)).isEmpty());
    }
    @Test void transportFailureNeverBecomesRejectionAndAutomaticRetriesAreBounded(){
        var guide=sent();remote=new SunatGreClient.GreStatusResult(false,"HTTP_503","No disponible",null,false,false);
        for(int attempt=0;attempt<8;attempt++)tx(em->service(em).checkStatus(guide.getId(),business));
        var state=read(guide.getId());assertEquals(DocumentStatus.ERROR,state.getStatus());assertEquals(8,state.getRetryCount());assertNull(state.getNextRetryAt());
        assertTrue(tx(em->service(em).findTicketsDue(20)).isEmpty());
    }
    @Test void printingRequiresProductionAcceptanceAndStoredCdrAndCannotBeEnabledByChangingTheTenantEnvironment(){
        var guide=sent();tx(em->{em.find(DispatchGuide.class,guide.getId()).setEnvironment("BETA");return null;});remote=new SunatGreClient.GreStatusResult(true,"0","Aceptada","<CDR><ResponseCode>0</ResponseCode></CDR>",false,false);
        tx(em->service(em).checkStatus(guide.getId(),business));assertFalse(tx(em->service(em).printable(em.find(DispatchGuide.class,guide.getId()))).booleanValue());
        tx(em->{service(em).tenantService.resolve(business).setEnvironment("PRODUCTION");return null;});
        assertFalse(tx(em->service(em).printable(em.find(DispatchGuide.class,guide.getId()))).booleanValue());
        tx(em->{em.find(DispatchGuide.class,guide.getId()).setEnvironment("PRODUCTION");return null;});
        assertTrue(tx(em->service(em).printable(em.find(DispatchGuide.class,guide.getId()))).booleanValue());
    }
    @Test void tenantAndChangedEnvironmentCannotClaimAnotherSubmission(){
        var guide=tx(em->service(em).emit(request()));configure();
        assertNull(tx(em->service(em).claimSubmission(guide.getId(),"B20999999999")));
        tx(em->{service(em).tenantService.resolve(business).setEnvironment("BETA");return null;});
        assertNull(tx(em->service(em).claimSubmission(guide.getId(),business)));assertNull(read(guide.getId()).getNextRetryAt());
        assertTrue(tx(em->service(em).findStates(List.of(guide.getId()),"B20999999999")).isEmpty());
    }
    // Pointer updates and encrypted versions must commit together in the real database.
    TenantCredentialsService credentialService(EntityManager em) {
        try {
            var directory = storage.resolve("secrets"); Files.createDirectories(directory);
            Files.writeString(directory.resolve("key"),"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
            var cipher = new io.carabo.app.security.CredentialCipher();
            inject(cipher,"basePath",directory.toString());inject(cipher,"keyReference","secret://key");
            var store = new io.carabo.app.security.ManagedCredentialStore();inject(store,"em",em);inject(store,"cipher",cipher);
            var resolver = new SecretResolver();inject(resolver,"managedCredentials",store);inject(resolver,"basePath",directory.toString());
            var result = new TenantCredentialsService();result.store=store;result.resolver=resolver;result.tenants=new TenantService();
            return result;
        } catch(Exception error){throw new IllegalStateException(error);}
    }
    static void inject(Object target,String name,Object value) throws Exception {var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    Tenant credentialTenant(EntityManager em){return em.createQuery("SELECT t FROM Tenant t WHERE t.businessCode=:bc",Tenant.class).setParameter("bc",business).getSingleResult();}
    @Test void credentialsAreEncryptedRotatableAndNeverReturnedByStatus() {
        String oldReference=tx(em->{var tenant=credentialTenant(em);credentialService(em).update(tenant,java.util.Map.of("greClientId","fixture-client","greClientSecret","fixture-secret=="),"test-actor");return tenant.getGreClientSecret();});
        tx(em->{var tenant=credentialTenant(em);var service=credentialService(em);
            assertEquals("fixture-secret==",service.resolver.resolve(oldReference));
            var status=service.status(tenant);assertTrue(status.fields().get("greClientSecret").usable());
            assertFalse(new ObjectMapper().valueToTree(status).toString().contains("fixture-secret"));
            var encrypted=em.createNativeQuery("SELECT encrypted_value FROM carabo.tenant_credential").getSingleResult().toString();assertFalse(encrypted.contains("fixture-secret"));
            assertTrue(service.store.belongsTo(oldReference,tenant.getId(),"greClientSecret"));assertFalse(service.store.belongsTo(oldReference,UUID.randomUUID(),"greClientSecret"));assertFalse(service.store.belongsTo(oldReference,tenant.getId(),"solPassword"));
            service.update(tenant,java.util.Map.of("greClientSecret","rotated-secret==","solPassword",""),"test-actor");
            assertNotEquals(oldReference,tenant.getGreClientSecret());assertEquals("rotated-secret==",service.resolver.resolve(tenant.getGreClientSecret()));assertEquals("fixture-secret==",service.resolver.resolve(oldReference));
            assertEquals("test-only",tenant.getSolPassword());return null;});
    }
    @Test void failedCredentialTransactionsLeaveNeitherNewPointersNorSecretRows() {
        assertThrows(IllegalStateException.class,()->tx(em->{credentialService(em).update(credentialTenant(em),java.util.Map.of("greClientSecret","rollback-secret","solPassword","rollback-sol"),"test");em.flush();throw new IllegalStateException("rollback");}));
        tx(em->{assertNull(credentialTenant(em).getGreClientSecret());assertEquals("test-only",credentialTenant(em).getSolPassword());assertEquals(0L,((Number)em.createNativeQuery("SELECT count(*) FROM carabo.tenant_credential").getSingleResult()).longValue());return null;});
    }
    @Test void statusReportsUnavailableWhenAStoredSecretBelongsToAnotherCompany() {
        tx(em->{var service=credentialService(em);var tenant=credentialTenant(em);var other=new Tenant();other.setBusinessCode("B20999999999");other.setIssuerRuc("20999999999");other.setIssuerName("Other company");em.persist(other);em.flush();
            String reference=service.store.persist(service.store.prepare(other.getId(),"greClientSecret","other-secret"),"test");tenant.setGreClientSecret(reference);
            assertTrue(service.status(tenant).fields().get("greClientSecret").configured());assertFalse(service.status(tenant).fields().get("greClientSecret").usable());return null;});
    }
    @Test void betaRequestsAreStoredWithoutAttemptingNetworkOrConsumingNumbers() {
        tx(em->{credentialTenant(em).setEnvironment("BETA");return null;});configure();
        var guide=tx(em->service(em).emit(request()));assertEquals(DocumentStatus.BLOCKED_CONFIGURATION,guide.getStatus());assertNull(guide.getNextRetryAt());assertNull(guide.getNumber());
        assertNull(tx(em->service(em).claimSubmission(guide.getId(),business)));
        assertThrows(jakarta.ws.rs.ClientErrorException.class,()->tx(em->service(em).requestRetry(guide.getId(),business,"U1")));
    }
    @Test void existingBetaRetriesAreBlockedImmediatelyEvenWhenTheirBackoffHasNotElapsed() {
        var guide=tx(em->service(em).emit(request()));
        tx(em->{credentialTenant(em).setEnvironment("BETA");var g=em.find(DispatchGuide.class,guide.getId());g.setEnvironment("BETA");g.setStatus(DocumentStatus.RETRY_PENDING);g.setNextRetryAt(Instant.now().plusSeconds(3600));return null;});
        assertEquals(1,tx(em->service(em).findSubmissionsDue(20)).size());
        assertNull(tx(em->service(em).claimSubmission(guide.getId(),business)));
        assertEquals(DocumentStatus.BLOCKED_CONFIGURATION,read(guide.getId()).getStatus());assertNull(read(guide.getId()).getNextRetryAt());
        assertTrue(tx(em->service(em).findSubmissionsDue(20)).isEmpty());
    }
    @Test void retrySchedulesTheSameGuideAndPreservesTheSavedErrorWithoutAnotherNumber() {
        var guide=tx(em->service(em).emit(request()));
        tx(em->{var g=em.find(DispatchGuide.class,guide.getId());g.setStatus(DocumentStatus.RETRY_PENDING);g.setRetryCount(4);g.setNextRetryAt(Instant.now().plusSeconds(480));g.setSunatResponse("Saved failure");return null;});
        var result=tx(em->service(em).requestRetry(guide.getId(),business,"U1")).orElseThrow();
        assertEquals(guide.getId(),result.getId());assertEquals(4,result.getRetryCount());assertEquals("Saved failure",result.getSunatResponse());assertTrue(result.getNextRetryAt().isBefore(Instant.now()));
        assertNull(tx(em->service(em).requestRetry(guide.getId(),"B20999999999","U1")).orElse(null));
        assertEquals(1L,tx(em->em.createQuery("SELECT count(g) FROM DispatchGuide g",Long.class).getSingleResult()).longValue());
    }
    @Test void uncertainAndRejectedGuidesCannotBeResentAndTicketRecoveryOnlyQueries() {
        var guide=sent();
        tx(em->{var g=em.find(DispatchGuide.class,guide.getId());g.setStatus(DocumentStatus.ERROR);g.setRetryCount(8);g.setNextRetryAt(null);return null;});
        var retry=tx(em->service(em).requestRetry(guide.getId(),business,"U1")).orElseThrow();assertEquals("ticket-test",retry.getSunatTicket());assertEquals(0,retry.getRetryCount());
        assertNull(tx(em->service(em).claimSubmission(guide.getId(),business)));
        tx(em->{var g=em.find(DispatchGuide.class,guide.getId());g.setStatus(DocumentStatus.SIGNED);return null;});
        assertThrows(jakarta.ws.rs.ClientErrorException.class,()->tx(em->service(em).requestRetry(guide.getId(),business,"U1")));
        tx(em->{em.find(DispatchGuide.class,guide.getId()).setStatus(DocumentStatus.REJECTED);return null;});
        assertThrows(jakarta.ws.rs.ClientErrorException.class,()->tx(em->service(em).requestRetry(guide.getId(),business,"U1")));
    }
    private DispatchGuide sent(){var guide=tx(em->service(em).emit(request()));configure();var submission=tx(em->service(em).claimSubmission(guide.getId(),business));tx(em->{service(em).recordSubmission(submission,new SunatGreClient.GreSendResult(true,"ticket-test",null,false,false));return null;});return read(guide.getId());}
    private String run(String...command)throws Exception{var process=new ProcessBuilder(command).redirectErrorStream(true).start();String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);if(process.waitFor()!=0)throw new IllegalStateException("Disposable database command failed");return output;}
}
