package io.carabo.app.service;

import io.carabo.core.entity.Document;
import io.carabo.core.enums.DocumentStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class CustomerMailOutboxTest {
    static final String BUSINESS = "B20123456789";
    static String container, url, password;
    static SessionFactory factory;
    Document document;

    static String docker(String... args) throws Exception {
        var command = new ArrayList<>(List.of("docker")); command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String result = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("Isolated database command failed");
        return result.trim();
    }

    @BeforeAll static void database() throws Exception {
        container = "carabo-customer-mail-test-" + UUID.randomUUID(); password = UUID.randomUUID().toString();
        docker("run", "--rm", "-d", "--name", container, "--memory=256m", "-e", "POSTGRES_PASSWORD=" + password, "-p", "127.0.0.1::5432", "postgres:16-alpine");
        String port = docker("port", container, "5432/tcp").split(":")[1];
        url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres";
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            try (var connection = DriverManager.getConnection(url, "postgres", password)) { break; }
            catch (java.sql.SQLException waiting) { if (System.nanoTime() > deadline) throw waiting; Thread.sleep(100); }
        }
        Path migrations = Path.of("../../../migrations");
        try (var connection = DriverManager.getConnection(url, "postgres", password); var statement = connection.createStatement()) {
            for (String file : List.of("001-document-schema.sql", "003-gre-and-apikey.sql", "008-operational-reliability.sql", "016-customer-document-mail.sql"))
                statement.execute(Files.readString(migrations.resolve(file)));
        }
        factory = new Configuration().setProperty("hibernate.connection.driver_class", "org.postgresql.Driver")
            .setProperty("hibernate.connection.url", url).setProperty("hibernate.connection.username", "postgres")
            .setProperty("hibernate.connection.password", password).setProperty("hibernate.hbm2ddl.auto", "none").buildSessionFactory();
    }

    @AfterAll static void close() throws Exception { if (factory != null) factory.close(); if (container != null) docker("rm", "-f", container); }

    @BeforeEach void fixture() throws Exception {
        document = new Document(); document.setId(UUID.randomUUID()); document.setBusinessCode(BUSINESS);
        document.setStatus(DocumentStatus.ACCEPTED); document.setReceiverEmail("guest@example.invalid");
        try (var connection = DriverManager.getConnection(url, "postgres", password); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE carabo.notification_outbox,carabo.document CASCADE");
            try (var insert = connection.prepareStatement("INSERT INTO carabo.document(id,business_code,doc_type,series,number,issue_date,issuer_ruc,issuer_name,receiver_doc_type,receiver_doc,receiver_name,status) VALUES(?,?,'RECEIPT','B001',1,current_date,'20123456789','Comercio','1','12345678','Cliente','ACCEPTED')")) {
                insert.setObject(1, document.getId()); insert.setString(2, BUSINESS); insert.executeUpdate();
            }
        }
    }

    <T> T transaction(Function<CustomerMailOutbox, T> action) {
        EntityManager em = factory.createEntityManager();
        try {
            em.getTransaction().begin(); var queue = new CustomerMailOutbox(); queue.em = em;
            T result = action.apply(queue); em.getTransaction().commit(); return result;
        } catch (RuntimeException failure) { if (em.getTransaction().isActive()) em.getTransaction().rollback(); throw failure; }
        finally { em.close(); }
    }

    long queuedRows() throws Exception {
        try (var connection = DriverManager.getConnection(url, "postgres", password); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM carabo.notification_outbox")) { rows.next(); return rows.getLong(1); }
    }

    @Test void concurrentFiscalCallbacksCreateOneCustomerMail() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) results.add(executor.submit(() -> transaction(queue -> { queue.enqueue(document); return true; })));
            for (var result : results) assertTrue(result.get());
        }
        assertEquals(1, queuedRows());
        var first = transaction(CustomerMailOutbox::claim);
        assertNotNull(first);
        assertNull(transaction(CustomerMailOutbox::claim));
    }

    @Test void preservesThePreparedRequestAndRejectsAnExpiredLease() throws Exception {
        transaction(queue -> { queue.enqueue(document); return true; });
        var first = transaction(CustomerMailOutbox::claim);
        String request = "{\"to\":\"guest@example.invalid\",\"subject\":\"Comprobante\",\"text\":\"Fiscal\"}";
        assertTrue(transaction(queue -> queue.prepared(first, request)).booleanValue());
        try (var connection = DriverManager.getConnection(url, "postgres", password); var statement = connection.createStatement()) { statement.execute("UPDATE carabo.notification_outbox SET lease_until=now()-interval '1 second'"); }
        var retry = transaction(CustomerMailOutbox::claim);
        assertEquals(request, retry.payload());
        assertEquals(2, retry.attempts());
        assertFalse(transaction(queue -> queue.prepared(first, "changed")).booleanValue());
        transaction(queue -> { queue.finish(first, true); return true; });
        assertNull(transaction(CustomerMailOutbox::claim));
        transaction(queue -> { queue.finish(retry, true); return true; });
        assertNull(transaction(CustomerMailOutbox::claim));
    }

    @Test void rollsBackTheMailIntentAndDoesNotSendRejectedDocuments() throws Exception {
        assertThrows(IllegalStateException.class, () -> transaction(queue -> { queue.enqueue(document); throw new IllegalStateException("rollback"); }));
        assertEquals(0, queuedRows());
        document.setStatus(DocumentStatus.REJECTED);
        transaction(queue -> { queue.enqueue(document); return true; });
        assertEquals(0, queuedRows());
    }
}
