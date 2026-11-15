package io.carabo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carabo.core.dto.EmitDocumentRequest;
import io.carabo.core.entity.Document;
import io.carabo.core.entity.Tenant;
import io.carabo.core.ubl.UblBuilder;
import io.carabo.core.validation.DocumentValidator;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import static org.junit.jupiter.api.Assertions.*;

class OdooCreditWireTest {
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private Tenant tenant() throws Exception {var issuer=mapper.readTree(getClass().getResourceAsStream("/commerce/odoo-credit-wire-v1.json")).path("issuer");var tenant=new Tenant();tenant.setIssuerRuc(issuer.path("issuerRuc").asText());tenant.setIssuerName(issuer.path("issuerName").asText());tenant.setIssuerAddress("Lima");tenant.setIssuerUbigeo("150101");return tenant;}

    @Test
    void nativePartialCreditPayloadsProduceExactReferencedUbl() throws Exception {
        var input=getClass().getResourceAsStream("/commerce/odoo-credit-wire-v1.json");assertNotNull(input);
        var payloads=mapper.readTree(input).path("payloads");assertEquals(6,payloads.size());
        var output=mapper.createArrayNode();
        for(var payload:payloads){
            assertTrue(new IntegrationPayloadValidator().validate(payload,payload.path("externalId").asText(),LocalDate.parse(payload.path("issueDate").asText())).valid());
            var request=mapper.treeToValue(payload,EmitDocumentRequest.class);request.setBusinessCode("B20123456789");
            Document document=new DocumentService().buildDocument(request,tenant());
            assertTrue(new DocumentValidator().validateStructure(document).isEmpty());
            var original=payload.path("lines").get(0).path("originalAmounts");
            assertEquals(0,new BigDecimal(original.path("net").asText()).compareTo(document.getTaxBase()));assertEquals(0,new BigDecimal(original.path("tax").asText()).compareTo(document.getIgvAmount()));
            var row=mapper.createObjectNode();row.set("payload",payload);row.put("xml",new UblBuilder().build(document));output.add(row);
        }
        String target=System.getenv("BIRDPORT_CARABO_CREDIT_REPORT");if(target!=null)Files.writeString(Path.of(target),mapper.writeValueAsString(output));
    }

    @Test
    void migrationPreservesExactUnitValuesAndPostedCentsAfterJdbcReload() throws Exception {
        String jdbc=System.getenv("CARABO_CREDIT_TEST_JDBC");Assumptions.assumeTrue(jdbc!=null);
        String password=System.getenv("CARABO_CREDIT_TEST_PASSWORD");
        var rows=new ArrayList<Document>();
        var payloads=mapper.readTree(getClass().getResourceAsStream("/commerce/odoo-credit-wire-v1.json")).path("payloads");
        try(var connection=DriverManager.getConnection(jdbc,"postgres",password);var sql=connection.createStatement()){
            var database=sql.executeQuery("SELECT current_database()");assertTrue(database.next());assertTrue(database.getString(1).endsWith("_birdport_credit_tests"));
            sql.execute("CREATE SCHEMA carabo;CREATE TABLE carabo.document_line(id uuid PRIMARY KEY,product_code varchar(50),unit_price numeric(18,6),gross_price numeric(18,6),quantity numeric(18,4),tax_base numeric(18,2),igv_amount numeric(18,2),line_total numeric(18,2))");
            String migration=Files.readString(Path.of(System.getenv("CARABO_CREDIT_MIGRATION_SQL")));sql.execute(migration);sql.execute(migration);
            for(var payload:payloads){var request=mapper.treeToValue(payload,EmitDocumentRequest.class);request.setBusinessCode("B20123456789");var document=new DocumentService().buildDocument(request,tenant());var line=document.getLines().get(0);line.setId(UUID.randomUUID());rows.add(document);
                try(var insert=connection.prepareStatement("INSERT INTO carabo.document_line(id,unit_price,gross_price,quantity,tax_base,igv_amount,line_total) VALUES(?,?,?,?,?,?,?)")){insert.setObject(1,line.getId());insert.setBigDecimal(2,line.getUnitPrice());insert.setBigDecimal(3,line.getGrossPrice());insert.setBigDecimal(4,line.getQuantity());insert.setBigDecimal(5,line.getTaxBase());insert.setBigDecimal(6,line.getIgvAmount());insert.setBigDecimal(7,line.getLineTotal());assertEquals(1,insert.executeUpdate());}
            }
        }
        try(var connection=DriverManager.getConnection(jdbc,"postgres",password)){
            for(var document:rows){var line=document.getLines().get(0);String originalXml=new UblBuilder().build(document);
                try(var query=connection.prepareStatement("SELECT unit_price,gross_price,quantity,tax_base,igv_amount,line_total FROM carabo.document_line WHERE id=?")){query.setObject(1,line.getId());try(var row=query.executeQuery()){assertTrue(row.next());assertEquals(0,line.getUnitPrice().compareTo(row.getBigDecimal(1)));line.setUnitPrice(row.getBigDecimal(1));line.setGrossPrice(row.getBigDecimal(2));line.setQuantity(row.getBigDecimal(3));line.setTaxBase(row.getBigDecimal(4));line.setIgvAmount(row.getBigDecimal(5));line.setLineTotal(row.getBigDecimal(6));}}
                // Numeric UBL representation is stable after exact values are loaded again.
                var actual=new UblBuilder().build(document);
                var parser=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
                var before=parser.parse(new java.io.ByteArrayInputStream(originalXml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                var after=parser.parse(new java.io.ByteArrayInputStream(actual.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                var xpath=javax.xml.xpath.XPathFactory.newInstance().newXPath();
                for(String expression:java.util.List.of("//*[local-name()='CreditNoteLine']/*[local-name()='CreditedQuantity']","//*[local-name()='CreditNoteLine']/*[local-name()='LineExtensionAmount']","//*[local-name()='CreditNoteLine']/*[local-name()='TaxTotal']/*[local-name()='TaxAmount']","//*[local-name()='CreditNoteLine']/*[local-name()='Price']/*[local-name()='PriceAmount']"))assertEquals(0,new BigDecimal(xpath.evaluate(expression,before)).compareTo(new BigDecimal(xpath.evaluate(expression,after))));
            }
        }
    }
}
