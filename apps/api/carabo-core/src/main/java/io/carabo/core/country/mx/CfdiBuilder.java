package io.carabo.core.country.mx;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Mexico SAT CFDI 4.0 XML builder.
 * Based on SAT Anexo 20 for: Ingreso (I), Egreso (E), Traslado (T).
 */
public class CfdiBuilder {

    private static final String CFDI_NS = "http://www.sat.gob.mx/cfd/4";
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String SCHEMA_LOC = "http://www.sat.gob.mx/cfd/4 http://www.sat.gob.mx/sitio_internet/cfd/4/cfdv40.xsd";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public String build(Document doc) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<cfdi:Comprobante\n");
        xml.append("  xmlns:cfdi=\"").append(CFDI_NS).append("\"\n");
        xml.append("  xmlns:xsi=\"").append(XSI).append("\"\n");
        xml.append("  xsi:schemaLocation=\"").append(SCHEMA_LOC).append("\"\n");
        xml.append("  Version=\"4.0\"\n");
        xml.append("  Serie=\"").append(doc.getSeries()).append("\"\n");
        xml.append("  Folio=\"").append(doc.getNumber()).append("\"\n");
        xml.append("  Fecha=\"").append(LocalDateTime.now().format(DT_FMT)).append("\"\n");
        xml.append("  FormaPago=\"01\"\n"); // 01=Efectivo (default)
        xml.append("  SubTotal=\"").append(doc.getTaxBase()).append("\"\n");
        xml.append("  Moneda=\"").append(mapMoneda(doc.getCurrency())).append("\"\n");
        xml.append("  Total=\"").append(doc.getTotal()).append("\"\n");
        xml.append("  TipoDeComprobante=\"").append(mapTipoComprobante(doc)).append("\"\n");
        xml.append("  MetodoPago=\"PUE\"\n"); // PUE=Pago en una sola exhibición
        xml.append("  LugarExpedicion=\"06600\"\n"); // CP del emisor
        xml.append("  Exportacion=\"01\">\n"); // 01=No aplica

        // Emisor
        xml.append("  <cfdi:Emisor\n");
        xml.append("    Rfc=\"").append(doc.getIssuerRuc()).append("\"\n");
        xml.append("    Nombre=\"").append(escapeXml(doc.getIssuerName())).append("\"\n");
        xml.append("    RegimenFiscal=\"601\"/>\n"); // 601=General de Ley

        // Receptor
        xml.append("  <cfdi:Receptor\n");
        xml.append("    Rfc=\"").append(doc.getReceiverDoc()).append("\"\n");
        xml.append("    Nombre=\"").append(escapeXml(doc.getReceiverName())).append("\"\n");
        xml.append("    DomicilioFiscalReceptor=\"06600\"\n");
        xml.append("    RegimenFiscalReceptor=\"601\"\n");
        xml.append("    UsoCFDI=\"G03\"/>\n"); // G03=Gastos en general

        // Conceptos
        xml.append("  <cfdi:Conceptos>\n");
        for (DocumentLine line : doc.getLines()) {
            xml.append("    <cfdi:Concepto\n");
            xml.append("      ClaveProdServ=\"").append(line.getProductCode() != null ? line.getProductCode() : "01010101").append("\"\n");
            xml.append("      NoIdentificacion=\"").append(line.getProductCode() != null ? line.getProductCode() : "").append("\"\n");
            xml.append("      Cantidad=\"").append(line.getQuantity()).append("\"\n");
            xml.append("      ClaveUnidad=\"").append(mapClaveUnidad(line.getUnitCode())).append("\"\n");
            xml.append("      Descripcion=\"").append(escapeXml(line.getProductName())).append("\"\n");
            xml.append("      ValorUnitario=\"").append(line.getUnitPrice()).append("\"\n");
            xml.append("      Importe=\"").append(line.getTaxBase()).append("\"\n");
            xml.append("      ObjetoImp=\"02\">\n"); // 02=Sí objeto de impuesto

            // Impuestos por concepto
            xml.append("      <cfdi:Impuestos>\n");
            xml.append("        <cfdi:Traslados>\n");
            xml.append("          <cfdi:Traslado\n");
            xml.append("            Base=\"").append(line.getTaxBase()).append("\"\n");
            xml.append("            Impuesto=\"002\"\n"); // 002=IVA
            xml.append("            TipoFactor=\"Tasa\"\n");
            xml.append("            TasaOCuota=\"0.160000\"\n");
            xml.append("            Importe=\"").append(line.getIgvAmount()).append("\"/>\n");
            xml.append("        </cfdi:Traslados>\n");
            xml.append("      </cfdi:Impuestos>\n");
            xml.append("    </cfdi:Concepto>\n");
        }
        xml.append("  </cfdi:Conceptos>\n");

        // Impuestos totales
        xml.append("  <cfdi:Impuestos TotalImpuestosTrasladados=\"").append(doc.getIgvAmount()).append("\">\n");
        xml.append("    <cfdi:Traslados>\n");
        xml.append("      <cfdi:Traslado\n");
        xml.append("        Base=\"").append(doc.getTaxBase()).append("\"\n");
        xml.append("        Impuesto=\"002\"\n");
        xml.append("        TipoFactor=\"Tasa\"\n");
        xml.append("        TasaOCuota=\"0.160000\"\n");
        xml.append("        Importe=\"").append(doc.getIgvAmount()).append("\"/>\n");
        xml.append("    </cfdi:Traslados>\n");
        xml.append("  </cfdi:Impuestos>\n");

        xml.append("</cfdi:Comprobante>");
        return xml.toString();
    }

    private String mapTipoComprobante(Document doc) {
        return switch (doc.getDocType()) {
            case INVOICE, RECEIPT -> "I"; // Ingreso
            case CREDIT_NOTE -> "E";      // Egreso
            case DEBIT_NOTE -> "I";       // Ingreso (débito = cargo adicional)
            default -> "I";
        };
    }

    private String mapMoneda(String currency) {
        return switch (currency) {
            case "MXN" -> "MXN";
            case "USD" -> "USD";
            case "PEN" -> "XXX"; // Fallback
            default -> currency;
        };
    }

    private String mapClaveUnidad(String unitCode) {
        return switch (unitCode) {
            case "NIU" -> "H87"; // Pieza
            case "KGM" -> "KGM";
            case "LTR" -> "LTR";
            default -> "H87";
        };
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
