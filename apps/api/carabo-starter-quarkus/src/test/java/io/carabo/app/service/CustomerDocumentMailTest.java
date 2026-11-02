package io.carabo.app.service;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDocumentMailTest {
    @Test
    void sendsTheAcceptedFiscalFilesToTheCheckoutRecipientAndLabelsBeta() {
        var document = new CustomerMailOutbox.MailDocument("B001", 4, "B001-00000004", "20123456789", "Comercio", "03", "79.00", "PEN", true);
        var mail = CustomerDocumentMail.prepare("guest@example.invalid", document, "<signed/>", "<cdr/>", new byte[] { 37, 80, 68, 70, 45 });
        assertEquals("guest@example.invalid", mail.get("to"));
        assertEquals("[PRUEBA BETA] Comprobante B001-00000004", mail.get("subject"));
        assertTrue(mail.get("text").toString().contains("PEN 79.00"));
        List<?> attachments = (List<?>) mail.get("attachments");
        assertEquals(3, attachments.size());
        Map<?, ?> pdf = (Map<?, ?>) attachments.get(0);
        assertEquals("application/pdf", pdf.get("contentType"));
        Map<?, ?> xml = (Map<?, ?>) attachments.get(1);
        assertEquals("20123456789-03-B001-00000004.xml", xml.get("filename"));
        assertEquals("<signed/>", new String(Base64.getDecoder().decode(xml.get("content").toString()), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void identifiesRetriesByTheSameDocumentAndWaitsForTheRequiredPdf() {
        UUID id = UUID.randomUUID();
        var first = new CustomerMailOutbox.Claim(UUID.randomUUID(), UUID.randomUUID(), "B20123456789", id, "guest@example.invalid", "{}", 1);
        var retry = new CustomerMailOutbox.Claim(first.id(), UUID.randomUUID(), first.business(), id, first.recipient(), "{}", 2);
        assertEquals(CustomerDocumentMail.operationId(first), CustomerDocumentMail.operationId(retry));
        var document = new CustomerMailOutbox.MailDocument("B001", 4, "B001-00000004", "20123456789", "Comercio", "03", "79.00", "PEN", false);
        assertThrows(IllegalStateException.class, () -> CustomerDocumentMail.prepare(first.recipient(), document, "<signed/>", "<cdr/>", null));
        assertThrows(IllegalStateException.class, () -> CustomerDocumentMail.prepare(first.recipient(), document, "<signed/>", "<cdr/>", "<html>error</html>".getBytes()));
        var mail = CustomerDocumentMail.prepare(first.recipient(), document, "<signed/>", "<cdr/>", "%PDF-test".getBytes());
        assertEquals(3, ((List<?>) mail.get("attachments")).size());
        assertFalse(mail.get("subject").toString().contains("BETA"));
    }

    @Test
    void validatesAnOptionalRecipientWithoutAcceptingMailHeaderInjection() {
        assertNull(CustomerMailOutbox.validRecipient(null));
        assertNull(CustomerMailOutbox.validRecipient(" "));
        assertEquals("guest@example.invalid", CustomerMailOutbox.validRecipient(" guest@example.invalid "));
        assertThrows(IllegalArgumentException.class, () -> CustomerMailOutbox.validRecipient("invalid"));
        assertThrows(IllegalArgumentException.class, () -> CustomerMailOutbox.validRecipient("guest@example.invalid\r\nBcc: other@example.invalid"));
    }
}
