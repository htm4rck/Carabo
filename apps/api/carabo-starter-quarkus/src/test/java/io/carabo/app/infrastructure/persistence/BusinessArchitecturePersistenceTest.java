package io.carabo.app.infrastructure.persistence;

import io.carabo.application.producer.ProducerContracts.CreateProducer;
import io.carabo.application.producer.ProducerUseCases;
import io.carabo.application.reporting.ReportContracts.ExportRow;
import io.carabo.app.security.ApiKeyService;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;
import io.carabo.core.entity.DocumentTax;
import io.carabo.core.entity.DocumentSendLog;
import io.carabo.core.entity.ProducerIntegration;
import io.carabo.core.entity.Tenant;
import io.carabo.core.enums.DocumentStatus;
import io.carabo.core.enums.DocumentType;
import io.carabo.domain.BusinessScope;
import io.carabo.domain.RuleViolation;
import io.carabo.domain.reporting.ReportPeriod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessArchitecturePersistenceTest {
    private static final BusinessScope A = new BusinessScope("TENANT_A");
    private static final BusinessScope B = new BusinessScope("TENANT_B");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    private String container;
    private EntityManagerFactory factory;

    @BeforeAll void database() throws Exception {
        container = "carabo-business-test-" + UUID.randomUUID().toString().substring(0, 8);
        String password = UUID.randomUUID().toString();
        run("docker", "run", "--rm", "-d", "--name", container, "--memory", "384m",
            "-e", "POSTGRES_PASSWORD=" + password, "-e", "POSTGRES_DB=carabo_architecture_test",
            "-p", "127.0.0.1::5432", "postgres:16-alpine", "-c", "shared_buffers=32MB", "-c", "max_connections=30");
        try {
            String port = run("docker", "port", container, "5432/tcp").trim();
            String url = "jdbc:postgresql://127.0.0.1:" + port.substring(port.lastIndexOf(':') + 1) + "/carabo_architecture_test";
            Exception pending = null;
            for (int attempt = 0; attempt < 60; attempt++) {
                try (var connection = DriverManager.getConnection(url, "postgres", password)) { pending = null; break; }
                catch (Exception error) { pending = error; Thread.sleep(100); }
            }
            if (pending != null) throw pending;
            Path repository = Path.of("").toAbsolutePath();
            while (repository != null && !Files.isRegularFile(repository.resolve("migrations/001-document-schema.sql"))) repository = repository.getParent();
            assertNotNull(repository);
            try (var connection = DriverManager.getConnection(url, "postgres", password);
                 var statement = connection.createStatement(); var files = Files.list(repository.resolve("migrations"))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".sql") && !p.getFileName().toString().startsWith("002-")).sorted().toList()) {
                    statement.execute(Files.readString(file));
                }
            }
            factory = new Configuration().addAnnotatedClass(ProducerIntegration.class).addAnnotatedClass(Tenant.class)
                .addAnnotatedClass(Document.class).addAnnotatedClass(DocumentLine.class).addAnnotatedClass(DocumentTax.class)
                .addAnnotatedClass(DocumentSendLog.class)
                .setProperty("hibernate.connection.url", url).setProperty("hibernate.connection.username", "postgres")
                .setProperty("hibernate.connection.password", password).setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
                .setProperty("hibernate.connection.pool_size", "16").setProperty("hibernate.hbm2ddl.auto", "validate")
                .setProperty("hibernate.generate_statistics", "true").setProperty("hibernate.session.events.log", "false").buildSessionFactory();
        } catch (Exception error) { cleanup(); throw error; }
    }

    @AfterAll void cleanup() throws Exception {
        if (factory != null) factory.close();
        if (container != null) run("docker", "rm", "-f", container);
    }

    @BeforeEach void reset() {
        tx(em -> {
            em.createNativeQuery("TRUNCATE carabo.document, carabo.producer_integration, carabo.tenant CASCADE").executeUpdate();
            for (String code : List.of("TENANT_A", "TENANT_B")) {
                var tenant = new Tenant(); tenant.setBusinessCode(code); tenant.setIssuerRuc("20123456789"); tenant.setIssuerName("Test issuer"); em.persist(tenant);
            }
            return null;
        });
    }

    @Test void reportsFilterTheCompanyAcceptedStateAndPeriodWithoutLoadingEntities() {
        tx(em -> {
            em.persist(document("TENANT_A", 1, DocumentStatus.ACCEPTED));
            em.persist(document("TENANT_A", 2, DocumentStatus.REJECTED));
            em.persist(document("TENANT_B", 1, DocumentStatus.ACCEPTED));
            Document outside = document("TENANT_A", 3, DocumentStatus.ACCEPTED);
            outside.setIssueDate(LocalDate.of(2026, 2, 1)); em.persist(outside);
            return null;
        });
        var statistics = factory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();
        tx(em -> {
            var reader = new JpaReportReader(); reader.em = em;
            var types = reader.byType(A, PERIOD);
            assertEquals(1, types.size());
            assertEquals(1, types.getFirst().count());
            assertEquals(new BigDecimal("118.00"), types.getFirst().total());
            assertEquals("INVOICE", types.getFirst().docType());
            var days = reader.byDay(A, PERIOD);
            assertEquals(1, days.size());
            assertEquals("2026-01-14", days.getFirst().date());
            var rows = new ArrayList<ExportRow>();
            assertEquals(1, reader.scanAccepted(A, PERIOD, rows::add));
            assertEquals("Customer TENANT_A", rows.getFirst().receiverName());
            assertTrue(reader.byType(new BusinessScope("TENANT_UNKNOWN"), PERIOD).isEmpty());
            return null;
        });
        assertEquals(0, statistics.getEntityLoadCount());
    }

    @Test void scansBeyondTheFetchWindowWithAStableOrderAndNoManagedDocuments() {
        tx(em -> { for (int i = 1; i <= 600; i++) em.persist(document("TENANT_A", i, DocumentStatus.ACCEPTED)); return null; });
        var statistics = factory.unwrap(org.hibernate.SessionFactory.class).getStatistics(); statistics.clear();
        tx(em -> {
            var reader = new JpaReportReader(); reader.em = em;
            int[] expected = {1};
            assertEquals(600, reader.scanAccepted(A, PERIOD, row -> assertEquals(expected[0]++, row.number().intValue())));
            assertEquals(601, expected[0]);
            return null;
        });
        assertEquals(0, statistics.getEntityLoadCount());
    }

    @Test void producerMutationsAndReadsRejectAnotherCompanyAndPreserveCredentialRotation() {
        var original = tx(em -> producers(em).create(A, create("erp-main")));
        assertTrue(tx(em -> producers(em).rotate(B, original.producer().id())).isEmpty());
        assertTrue(tx(em -> producers(em).setActive(B, original.producer().id(), false)).isEmpty());
        assertTrue(tx(em -> producers(em).list(B)).isEmpty());
        var rotated = tx(em -> producers(em).rotate(A, original.producer().id())).orElseThrow();
        assertTrue(tx(em -> producers(em).authenticate(original.apiKey())).isEmpty());
        assertEquals(original.producer().id(), tx(em -> producers(em).authenticate(rotated.apiKey())).orElseThrow().id());
        tx(em -> producers(em).setActive(A, original.producer().id(), false));
        assertTrue(tx(em -> producers(em).authenticate(rotated.apiKey())).isEmpty());
    }

    @Test void uniqueConflictsDoNotHideOtherPersistenceFailures() {
        tx(em -> producers(em).create(A, create("erp-main")));
        RuleViolation conflict = assertThrows(RuleViolation.class, () -> tx(em -> producers(em).create(A, create("erp-main"))));
        assertEquals(RuleViolation.Kind.CONFLICT, conflict.kind());
        assertEquals(1, tx(em -> producers(em).list(A)).size());
        assertThrows(jakarta.persistence.PersistenceException.class,
            () -> tx(em -> producers(em).create(new BusinessScope("TENANT_UNKNOWN"), create("erp-main"))));
    }

    @Test void tenantAndProducerChangesParticipateInTheSameCallerTransaction() {
        assertThrows(IllegalStateException.class, () -> tx(em -> {
            var tenant = new Tenant(); tenant.setBusinessCode("TENANT_C"); tenant.setIssuerRuc("20123456789"); tenant.setIssuerName("Test issuer"); em.persist(tenant);
            producers(em).syncLegacyCredential(new BusinessScope("TENANT_C"), new ApiKeyService().hash("test-key"), CLOCK.instant());
            throw new IllegalStateException("Caller rollback");
        }));
        tx(em -> {
            assertEquals(0L, em.createQuery("SELECT COUNT(t) FROM Tenant t WHERE t.businessCode = 'TENANT_C'", Long.class).getSingleResult());
            assertEquals(0L, em.createQuery("SELECT COUNT(p) FROM ProducerIntegration p", Long.class).getSingleResult());
            return null;
        });
    }

    @Test void listingUsesOneAggregateQueryAndDoesNotCountDocumentsFromOtherCompanies() {
        var first = tx(em -> producers(em).create(A, create("erp-main")));
        tx(em -> producers(em).create(A, create("erp-other")));
        tx(em -> {
            Document own = document("TENANT_A", 1, DocumentStatus.ACCEPTED); own.setProducerId(first.producer().id()); em.persist(own);
            Document foreign = document("TENANT_B", 1, DocumentStatus.ACCEPTED); foreign.setProducerId(first.producer().id()); em.persist(foreign);
            return null;
        });
        var statistics = factory.unwrap(org.hibernate.SessionFactory.class).getStatistics(); statistics.clear();
        var rows = tx(em -> producers(em).list(A));
        assertEquals(2, rows.size());
        assertEquals(1, rows.stream().mapToLong(value -> value.totalDocuments()).sum());
        assertEquals(1, statistics.getQueryExecutionCount());
    }

    @Test void activityCountersAreAtomicAndScopedUnderConcurrentRequests() {
        var created = tx(em -> producers(em).create(A, create("erp-main")));
        UUID id = created.producer().id();
        tx(em -> { producers(em).recordActivity(B, id, 500); return null; });
        assertEquals(0, tx(em -> producers(em).list(A)).getFirst().totalRequests());
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                tasks.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < 10; i++) tx(em -> { producers(em).recordActivity(A, id, 503); return null; });
                }, pool));
            }
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        }
        var producer = tx(em -> producers(em).list(A)).getFirst();
        assertEquals(80, producer.totalRequests());
        assertEquals(80, producer.failureRequests());
        assertEquals("DEGRADED", producer.health());
    }

    private ProducerUseCases producers(EntityManager em) {
        var repository = new JpaProducerRepository(); repository.em = em;
        return new ProducerUseCases(repository, repository, new ApiKeyService(), CLOCK);
    }
    private CreateProducer create(String code) { return new CreateProducer(code, "Test ERP", null, Set.of("DOCUMENT_READ")); }
    private Document document(String business, int number, DocumentStatus status) {
        var document = new Document();
        document.setBusinessCode(business); document.setDocType(DocumentType.INVOICE); document.setSeries("F001"); document.setNumber(number);
        document.setIssueDate(LocalDate.of(2026, 1, 14)); document.setIssuerRuc("20123456789"); document.setIssuerName("Test issuer");
        document.setReceiverDocType("6"); document.setReceiverDoc("20999999991"); document.setReceiverName("Customer " + business);
        document.setTaxBase(new BigDecimal("100.00")); document.setIgvAmount(new BigDecimal("18.00")); document.setTotal(new BigDecimal("118.00"));
        document.setStatus(status);
        return document;
    }
    private <T> T tx(Function<EntityManager, T> operation) {
        try (var em = factory.createEntityManager()) {
            var tx = em.getTransaction(); tx.begin();
            try { T result = operation.apply(em); tx.commit(); return result; }
            catch (RuntimeException error) { if (tx.isActive()) tx.rollback(); throw error; }
        }
    }
    private String run(String... command) throws Exception {
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("Disposable test database command failed");
        return output;
    }
}
