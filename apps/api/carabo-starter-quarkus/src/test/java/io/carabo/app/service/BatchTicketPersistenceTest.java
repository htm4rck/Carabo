package io.carabo.app.service;

import io.carabo.core.entity.*;
import io.carabo.core.enums.*;
import io.carabo.app.security.SecretResolver;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.ws.rs.NotFoundException;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable PostgreSQL proves transaction boundaries, tenant isolation and late-query recovery. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchTicketPersistenceTest {
    String container, password, url;
    EntityManagerFactory factory;
    final String business = "B20123456789", ticket = "owned-original-ticket";
    UUID operationId, documentId;
    final FiscalClock clock = new FiscalClock("America/Lima");

    @BeforeAll void database() throws Exception {
        container = "carabo-batch-tests-" + UUID.randomUUID().toString().substring(0,8); password = UUID.randomUUID().toString();
        run("docker","run","--rm","-d","--name",container,"-e","POSTGRES_PASSWORD="+password,"-e","POSTGRES_DB=carabo_batch_tests","-p","127.0.0.1::5432","postgres:16-alpine");
        String mapped = run("docker","port",container,"5432/tcp").trim();
        url = "jdbc:postgresql://127.0.0.1:" + mapped.substring(mapped.lastIndexOf(':')+1) + "/carabo_batch_tests";
        for(int n=0;;n++){try(var c=DriverManager.getConnection(url,"postgres",password)){break;}catch(Exception e){if(n==60)throw e;Thread.sleep(100);}}
        Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("migrations/014-deferred-dispatch-guides.sql")))root=root.getParent();assertNotNull(root);
        try(var c=DriverManager.getConnection(url,"postgres",password);var statement=c.createStatement();var paths=Files.list(root.resolve("migrations"))){
            for(Path file:paths.filter(p->p.getFileName().toString().endsWith(".sql")&&!p.getFileName().toString().startsWith("002-")).sorted().toList())statement.execute(Files.readString(file));
        }
        factory = new Configuration().addAnnotatedClass(Tenant.class).addAnnotatedClass(BatchOperation.class)
            .addAnnotatedClass(Document.class).addAnnotatedClass(DocumentLine.class).addAnnotatedClass(DocumentTax.class).addAnnotatedClass(DocumentSendLog.class)
            .setProperty("hibernate.connection.url",url+"?ApplicationName=carabo-batch-tests")
            .setProperty("hibernate.connection.username","postgres").setProperty("hibernate.connection.password",password)
            .setProperty("hibernate.hbm2ddl.auto","validate").buildSessionFactory();
    }
    @AfterAll void cleanup() throws Exception { if(factory!=null)factory.close(); if(container!=null)run("docker","stop",container); }
    <T>T tx(Function<EntityManager,T> body){try(var em=factory.createEntityManager()){var tx=em.getTransaction();tx.begin();try{T result=body.apply(em);tx.commit();return result;}catch(RuntimeException e){tx.rollback();throw e;}}}
    BatchTicketStore store(EntityManager em){var store=new BatchTicketStore();store.em=em;store.clock=clock;store.tenants=new TenantService();store.tenants.em=em;return store;}
    BatchTicketService service(Function<BatchTicketStore.Snapshot,BatchTicketStore.Outcome> query){
        var result=new BatchTicketService(){@Override protected BatchTicketStore.Outcome query(BatchTicketStore.Snapshot value){return query.apply(value);}};
        result.store=new BatchTicketStore(){
            @Override public Snapshot prepare(String business,String ticket){return tx(em->store(em).prepare(business,ticket));}
            @Override public BatchService.BatchResult complete(Snapshot snapshot,Outcome outcome){return tx(em->store(em).complete(snapshot,outcome));}
        };return result;
    }
    @BeforeEach void seed(){tx(em->{
        em.createNativeQuery("TRUNCATE carabo.batch_operation,carabo.document,carabo.tenant CASCADE").executeUpdate();
        var tenant=new Tenant();tenant.setBusinessCode(business);tenant.setBusinessName("Owned test");tenant.setIssuerRuc("20123456789");tenant.setIssuerName("Owned test");tenant.setIssuerAddress("Synthetic address");tenant.setIssuerUbigeo("150101");tenant.setEnvironment("BETA");tenant.setActive(true);em.persist(tenant);
        var doc=new Document();doc.setBusinessCode(business);doc.setDocType(DocumentType.INVOICE);doc.setSeries("FZ15");doc.setNumber(1);doc.setIssueDate(LocalDate.now());doc.setIssuerRuc("20123456789");doc.setIssuerName("Owned test");doc.setReceiverDocType("6");doc.setReceiverDoc("20100070970");doc.setReceiverName("Owned receiver");doc.setStatus(DocumentStatus.VOID_REQUESTED);em.persist(doc);em.flush();documentId=doc.getId();
        var op=new BatchOperation();op.setBusinessCode(business);op.setKind("RA");op.setIdentifier("20123456789-RA-owned");op.setRequestKey("owned-void");op.setRequestHash("hash");op.setDocumentIds(doc.getId().toString());op.setDocumentCount(1);op.setStatus("SENT");op.setTicket(ticket);em.persist(op);em.flush();operationId=op.getId();return null;
    });}
    @Test void timeoutPersistsOutsideTheRemoteCallAndTheSameTicketRecoversWithoutAnotherBatch(){
        var failed=service(snapshot->{
            try(var c=DriverManager.getConnection(url,"postgres",password);var st=c.createStatement();var rows=st.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE datname='carabo_batch_tests' AND application_name='carabo-batch-tests' AND xact_start IS NOT NULL")){
                assertTrue(rows.next());assertEquals(0,rows.getInt(1),"The remote wait must not retain a database transaction");
            }catch(Exception e){throw new IllegalStateException(e);}
            return new BatchTicketStore.Outcome(false,"ERROR","Receiver timed out",null);
        }).check(business,ticket);
        assertEquals("ERROR",failed.status());assertEquals(ticket,failed.ticket());assertEquals(operationId,failed.id());
        assertEquals(DocumentStatus.VOID_REQUESTED,tx(em->em.find(Document.class,documentId).getStatus()));
        var accepted=service(snapshot->new BatchTicketStore.Outcome(true,"ACCEPTED","Verified CDR","<CDR/>"));
        assertEquals("ACCEPTED",accepted.check(business,ticket).status());
        assertEquals(DocumentStatus.VOIDED,tx(em->em.find(Document.class,documentId).getStatus()));
        assertEquals(1L,tx(em->em.createQuery("SELECT count(b) FROM BatchOperation b",Long.class).getSingleResult()).longValue());
        assertEquals("ACCEPTED",service(snapshot->{fail("A terminal ticket must not call the receiver again");return null;}).check(business,ticket).status());
    }
    @Test void anOlderFailureCannotOverwriteAConcurrentAcceptedReceipt(){
        var result=service(snapshot->{
            tx(em->store(em).complete(snapshot,new BatchTicketStore.Outcome(true,"ACCEPTED","Verified CDR","<CDR/>")));
            return new BatchTicketStore.Outcome(false,"ERROR","Late timeout",null);
        }).check(business,ticket);
        assertEquals("ACCEPTED",result.status());assertEquals(DocumentStatus.VOIDED,tx(em->em.find(Document.class,documentId).getStatus()));
    }
    @Test void anotherBusinessCannotReadTheTicketOrApplyItsResult(){
        assertThrows(IllegalArgumentException.class,()->service(snapshot->{fail("No cross-tenant request");return null;}).check("B20999999990",ticket));
        var snapshot=tx(em->store(em).prepare(business,ticket));
        var foreign=new BatchTicketStore.Snapshot(snapshot.operationId(),"B20999999990",ticket,snapshot.mode(),snapshot.endpoint(),snapshot.solUser(),snapshot.passwordRef(),snapshot.oseUrl(),snapshot.oseTokenRef(),null);
        assertThrows(NotFoundException.class,()->tx(em->store(em).complete(foreign,new BatchTicketStore.Outcome(true,"ACCEPTED","foreign","<CDR/>"))));
        assertEquals(DocumentStatus.VOID_REQUESTED,tx(em->em.find(Document.class,documentId).getStatus()));
    }
    @Test void fiscalRejectionRestoresOnlyTheFiscalDocumentState(){
        var result=service(snapshot->new BatchTicketStore.Outcome(false,"REJECTED","Rejected CDR","<CDR/>"));
        assertEquals("REJECTED",result.check(business,ticket).status());
        assertEquals(DocumentStatus.ACCEPTED,tx(em->em.find(Document.class,documentId).getStatus()));
    }
    @Test void aCredentialFaultIsNotAFiscalRejectionAndNeverReleasesThePendingVoid() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/billService",exchange->{try(exchange){exchange.getRequestBody().readAllBytes();byte[] body="<Envelope><Fault><faultcode>soap:Client.0100</faultcode><faultstring>Invalid credentials</faultstring></Fault></Envelope>".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(500,body.length);exchange.getResponseBody().write(body);}});server.start();
        try {
            tx(em->{var tenant=em.createQuery("SELECT t FROM Tenant t WHERE t.businessCode=:bc",Tenant.class).setParameter("bc",business).getSingleResult();tenant.setSolUser("20123456789MODDATOS");tenant.setSolPassword("owned-test-only");tenant.setSunatEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/billService");return null;});
            var actual=new BatchTicketService();actual.store=service(snapshot->null).store;actual.secrets=new SecretResolver();
            assertEquals("ERROR",actual.check(business,ticket).status());
            assertEquals(DocumentStatus.VOID_REQUESTED,tx(em->em.find(Document.class,documentId).getStatus()));
        } finally { server.stop(0); }
    }
    @Test void replayAfterAQueryErrorReturnsItsOriginalTicketWithoutResubmitting(){
        String reason="Owned void";
        String hash;try{hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((List.of(documentId)+":"+reason).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
        tx(em->{var op=em.find(BatchOperation.class,operationId);op.setStatus("ERROR");op.setRequestHash(hash);return null;});
        var replay=tx(em->{var service=new BatchService();service.em=em;service.fiscalClock=clock;service.idempotencyService=new IdempotencyService();service.idempotencyService.em=em;return service.sendVoidedCommunication(business,List.of(documentId),reason,"owned-void");});
        assertEquals(operationId,replay.id());assertEquals(ticket,replay.ticket());assertEquals("ERROR",replay.status());assertFalse(replay.success());
    }
    private String run(String...command)throws Exception{var p=new ProcessBuilder(command).redirectErrorStream(true).start();String output=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);if(p.waitFor()!=0)throw new IllegalStateException("Disposable batch database failed");return output;}
}
