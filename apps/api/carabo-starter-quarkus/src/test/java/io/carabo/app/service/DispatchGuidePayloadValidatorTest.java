package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchGuidePayloadValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DispatchGuidePayloadValidator validator = new DispatchGuidePayloadValidator();
    private final LocalDate today = LocalDate.of(2026, 10, 16);

    @Test
    void acceptsCompletePrivateTransportGuide() throws Exception {
        var payload = mapper.readTree("""
            {
              "series":"T001","issueDate":"2026-10-16","startDate":"2026-10-16",
              "receiver":{"docType":"6","doc":"20131312955","name":"CLIENTE EMPRESA SAC"},
              "transportMode":"02","grossWeight":120.5,"weightUnit":"KGM","packageCount":4,
              "origin":{"ubigeo":"150101","address":"Av. Industrial 123, Lima"},
              "destination":{"ubigeo":"040101","address":"Av. Logística 456, Arequipa"},
              "driver":{"docType":"1","doc":"12345678","name":"Ana Torres","license":"Q12345678"},
              "vehiclePlate":"ABC123","transferReasonCode":"01","transferReasonDesc":"Venta",
              "relatedDoc":{"docType":"01","number":"F001-18452"},
              "items":[{"productCode":"PROD-001","productName":"Producto de prueba","unitCode":"NIU","quantity":4,"weight":120.5}],
              "callbackUrl":"https://erp.example.com/webhooks/carabo","externalId":"gre-20261016-0001"
            }
            """);

        var report = validator.validate(payload, "gre-20261016-0001", today);

        assertTrue(report.valid());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    void reportsExactPathsForIncompletePrivateTransportGuide() throws Exception {
        var payload = mapper.readTree("""
            {"series":"F001","issueDate":"2026-10-16","startDate":"2026-10-15",
             "receiver":{},"transportMode":"02","grossWeight":0,"packageCount":1,
             "origin":{"ubigeo":"15","address":"Lima"},"destination":{},
             "transferReasonCode":"99","items":[],"externalId":"other"}
            """);

        var report = validator.validate(payload, "gre-1", today);

        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.path().equals("$.series")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.path().equals("$.driver")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.path().equals("$.vehiclePlate")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.path().equals("$.origin.ubigeo")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.path().equals("$.externalId")));
    }
}
