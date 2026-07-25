package io.carabo.core.country.cl;

import io.carabo.core.entity.Document;
import io.carabo.core.entity.DocumentLine;

import java.time.format.DateTimeFormatter;

/**
 * Chile SII DTE (Documento Tributario Electrónico) XML builder.
 * Based on SII format for: Factura (33), Factura Exenta (34), NC (61), ND (56).
 */
public class SiiDteBuilder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public String build(Document doc) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n");
        xml.append("<DTE version=\"1.0\">\n");
        xml.append("  <Documento ID=\"DTE-").append(doc.getFullNumber()).append("\">\n");

        appendEncabezado(xml, doc);
        appendDetalle(xml, doc);

        if (doc.getRefSeries() != null) {
            appendReferencia(xml, doc);
        }

        xml.append("    <TED version=\"1.0\">\n");
        xml.append("      <!-- Timbre Electrónico - populated after signing -->\n");
        xml.append("    </TED>\n");

        xml.append("  </Documento>\n");
        xml.append("</DTE>");
        return xml.toString();
    }

    private void appendEncabezado(StringBuilder xml, Document doc) {
        xml.append("    <Encabezado>\n");

        // IdDoc
        xml.append("      <IdDoc>\n");
        xml.append("        <TipoDTE>").append(mapTipoDte(doc)).append("</TipoDTE>\n");
        xml.append("        <Folio>").append(doc.getNumber()).append("</Folio>\n");
        xml.append("        <FchEmis>").append(doc.getIssueDate().format(DATE_FMT)).append("</FchEmis>\n");
        if (doc.getDueDate() != null) {
            xml.append("        <FchVenc>").append(doc.getDueDate().format(DATE_FMT)).append("</FchVenc>\n");
        }
        xml.append("      </IdDoc>\n");

        // Emisor
        xml.append("      <Emisor>\n");
        xml.append("        <RUTEmisor>").append(doc.getIssuerRuc()).append("</RUTEmisor>\n");
        xml.append("        <RznSoc><![CDATA[").append(doc.getIssuerName()).append("]]></RznSoc>\n");
        if (doc.getIssuerTrade() != null) {
            xml.append("        <GiroEmis><![CDATA[").append(doc.getIssuerTrade()).append("]]></GiroEmis>\n");
        }
        if (doc.getIssuerAddress() != null) {
            xml.append("        <DirOrigen><![CDATA[").append(doc.getIssuerAddress()).append("]]></DirOrigen>\n");
        }
        xml.append("      </Emisor>\n");

        // Receptor
        xml.append("      <Receptor>\n");
        xml.append("        <RUTRecep>").append(doc.getReceiverDoc()).append("</RUTRecep>\n");
        xml.append("        <RznSocRecep><![CDATA[").append(doc.getReceiverName()).append("]]></RznSocRecep>\n");
        if (doc.getReceiverAddress() != null) {
            xml.append("        <DirRecep><![CDATA[").append(doc.getReceiverAddress()).append("]]></DirRecep>\n");
        }
        xml.append("      </Receptor>\n");

        // Totales
        xml.append("      <Totales>\n");
        xml.append("        <MntNeto>").append(doc.getTaxBase().longValue()).append("</MntNeto>\n");
        xml.append("        <TasaIVA>19</TasaIVA>\n");
        xml.append("        <IVA>").append(doc.getIgvAmount().longValue()).append("</IVA>\n");
        xml.append("        <MntTotal>").append(doc.getTotal().longValue()).append("</MntTotal>\n");
        xml.append("      </Totales>\n");

        xml.append("    </Encabezado>\n");
    }

    private void appendDetalle(StringBuilder xml, Document doc) {
        for (DocumentLine line : doc.getLines()) {
            xml.append("    <Detalle>\n");
            xml.append("      <NroLinDet>").append(line.getLineNumber()).append("</NroLinDet>\n");
            if (line.getProductCode() != null) {
                xml.append("      <CdgItem><TpoCodigo>INT1</TpoCodigo><VlrCodigo>").append(line.getProductCode()).append("</VlrCodigo></CdgItem>\n");
            }
            xml.append("      <NmbItem><![CDATA[").append(line.getProductName()).append("]]></NmbItem>\n");
            xml.append("      <QtyItem>").append(line.getQuantity()).append("</QtyItem>\n");
            xml.append("      <UnmdItem>").append(line.getUnitCode()).append("</UnmdItem>\n");
            xml.append("      <PrcItem>").append(line.getUnitPrice().longValue()).append("</PrcItem>\n");
            xml.append("      <MontoItem>").append(line.getTaxBase().longValue()).append("</MontoItem>\n");
            xml.append("    </Detalle>\n");
        }
    }

    private void appendReferencia(StringBuilder xml, Document doc) {
        xml.append("    <Referencia>\n");
        xml.append("      <NroLinRef>1</NroLinRef>\n");
        xml.append("      <TpoDocRef>").append(mapTipoRef(doc.getRefDocType())).append("</TpoDocRef>\n");
        xml.append("      <FolioRef>").append(doc.getRefNumber()).append("</FolioRef>\n");
        xml.append("      <CodRef>1</CodRef>\n"); // 1=Anula, 2=Corrige, 3=Devuelve
        if (doc.getRefReason() != null) {
            xml.append("      <RazonRef><![CDATA[").append(doc.getRefReason()).append("]]></RazonRef>\n");
        }
        xml.append("    </Referencia>\n");
    }

    private int mapTipoDte(Document doc) {
        return switch (doc.getDocType()) {
            case INVOICE -> 33;   // Factura Electrónica
            case RECEIPT -> 39;   // Boleta Electrónica
            case CREDIT_NOTE -> 61; // Nota de Crédito Electrónica
            case DEBIT_NOTE -> 56;  // Nota de Débito Electrónica
            default -> 33;
        };
    }

    private int mapTipoRef(String refDocType) {
        if (refDocType == null) return 33;
        return switch (refDocType) {
            case "01" -> 33;
            case "03" -> 39;
            default -> 33;
        };
    }
}
