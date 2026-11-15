package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationPayloadValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final IntegrationPayloadValidator validator = new IntegrationPayloadValidator();
    private final LocalDate today = LocalDate.of(2026, 10, 16);

    @Test
    void acceptsOnlyBoundedOriginalCentsForAReferencedCredit() throws Exception {
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree("""
            {"docType":"07","series":"BC01","number":3,"issueDate":"2026-10-16","currency":"PEN","externalId":"original-cent",
             "receiver":{"docType":"1","doc":"87654321","name":"Synthetic"},
             "reference":{"docType":"03","series":"B001","number":1,"reasonCode":"07","reason":"Item return"},
             "lines":[{"productCode":"CENT","productName":"Original cent","unitCode":"NIU","quantity":1,"unitPrice":0.03,"discount":0,"affectationCode":"10","originalAmounts":{"net":"0.03","tax":"0.00"}}]}
            """);
        assertTrue(validator.validate(payload,"original-cent",today).valid());
        payload.put("number",0);assertFalse(validator.validate(payload,"original-cent",today).valid());payload.put("number",3);
        var original=(com.fasterxml.jackson.databind.node.ObjectNode)payload.path("lines").get(0).path("originalAmounts");
        original.put("tax","0.03");assertFalse(validator.validate(payload,"original-cent",today).valid());original.put("tax","0.00");
        original.put("net",0.03);assertFalse(validator.validate(payload,"original-cent",today).valid());original.put("net","0.03");
        payload.put("docType","03");assertFalse(validator.validate(payload,"original-cent",today).valid());
    }

    @Test
    void acceptsCompleteInvoiceWithoutWarnings() throws Exception {
        var payload = mapper.readTree("""
            {"docType":"01","series":"F001","issueDate":"2026-10-16","currency":"PEN",
             "externalId":"erp-18452","receiver":{"docType":"6","doc":"20131312955","name":"Cliente SAC"},
             "lines":[{"productCode":"SERV-001","productName":"Servicio mensual","unitCode":"NIU",
                       "quantity":1,"unitPrice":100.00,"affectationCode":"10"}]}
            """);

        var report = validator.validate(payload, "erp-18452", today);

        assertTrue(report.valid());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    void returnsJsonPathsExpectedValuesAndSuggestions() throws Exception {
        var payload = mapper.readTree("""
            {"docType":"01","series":"B1","externalId":"body-id",
             "receiver":{"docType":"1","doc":"123","name":""},
             "lines":[{"productName":"","quantity":0,"unitPrice":"100","affectationCode":"99"}],
             "unexpected":true}
            """);

        var report = validator.validate(payload, "header-id", today);

        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(i -> i.path().equals("$.series") && i.severity().equals("ERROR")));
        assertTrue(report.issues().stream().anyMatch(i -> i.path().equals("$.receiver.docType") && i.expected().equals("6")));
        assertTrue(report.issues().stream().anyMatch(i -> i.path().equals("$.lines[0].unitPrice") && i.suggestion() != null));
        assertTrue(report.issues().stream().anyMatch(i -> i.path().equals("$.externalId") && i.code().equals("IDEMPOTENCY_CONFLICT")));
        assertTrue(report.issues().stream().anyMatch(i -> i.path().equals("$.unexpected") && i.severity().equals("WARNING")));
    }

    @Test
    void requiresReferenceForCreditAndDebitNotes() throws Exception {
        var payload = mapper.readTree("""
            {"docType":"07","series":"F001","externalId":"note-1",
             "receiver":{"docType":"6","doc":"20131312955","name":"Cliente SAC"},
             "lines":[{"productName":"Ajuste","quantity":1,"unitPrice":10}]}
            """);

        var report = validator.validate(payload, "note-1", today);

        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(i -> i.path().equals("$.reference")));
    }

    @Test
    void rejectsReceiptWithRucAndNoteWithMismatchedSeriesFamily() throws Exception {
        var receipt = mapper.readTree("""
            {"docType":"03","series":"B001","externalId":"receipt-1",
             "receiver":{"docType":"6","doc":"20131312955","name":"Cliente SAC"},
             "lines":[{"productName":"Venta","quantity":1,"unitPrice":10}]}
            """);
        var note = mapper.readTree("""
            {"docType":"07","series":"B001","externalId":"note-2",
             "receiver":{"docType":"6","doc":"20131312955","name":"Cliente SAC"},
             "lines":[{"productName":"Ajuste","quantity":1,"unitPrice":10}],
             "reference":{"docType":"01","series":"F001","number":123,"reasonCode":"01","reason":"Anulación"}}
            """);

        assertTrue(validator.validate(receipt, "receipt-1", today).issues().stream()
            .anyMatch(i -> i.code().equals("RECEIPT_WITH_RUC")));
        assertTrue(validator.validate(note, "note-2", today).issues().stream()
            .anyMatch(i -> i.code().equals("NOTE_SERIES_FAMILY")));
    }
}
