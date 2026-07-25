package io.carabo.core.ubl.gre;

import io.carabo.core.entity.gre.DispatchGuide;
import io.carabo.core.entity.gre.DispatchGuideItem;

import java.time.format.DateTimeFormatter;

/**
 * Generates UBL 2.1 DespatchAdvice XML for SUNAT GRE 2.0.
 */
public class GreUblBuilder {

    private static final String DA_NS = "urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2";
    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final String DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public String build(DispatchGuide gre) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<DespatchAdvice xmlns=\"").append(DA_NS).append("\"\n");
        xml.append("  xmlns:cac=\"").append(CAC).append("\"\n");
        xml.append("  xmlns:cbc=\"").append(CBC).append("\"\n");
        xml.append("  xmlns:ext=\"").append(EXT).append("\"\n");
        xml.append("  xmlns:ds=\"").append(DS).append("\">\n");

        // Extensions (signature placeholder)
        xml.append("  <ext:UBLExtensions>\n");
        xml.append("    <ext:UBLExtension><ext:ExtensionContent></ext:ExtensionContent></ext:UBLExtension>\n");
        xml.append("  </ext:UBLExtensions>\n");

        xml.append("  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>2.0</cbc:CustomizationID>\n");
        xml.append("  <cbc:ID>").append(gre.getFullNumber()).append("</cbc:ID>\n");
        xml.append("  <cbc:IssueDate>").append(gre.getIssueDate().format(DATE_FMT)).append("</cbc:IssueDate>\n");
        xml.append("  <cbc:DespatchAdviceTypeCode>09</cbc:DespatchAdviceTypeCode>\n");

        // Note (optional)
        xml.append("  <cbc:Note>GUIA DE REMISION ELECTRONICA</cbc:Note>\n");

        // Signature
        xml.append("  <cac:Signature>\n");
        xml.append("    <cbc:ID>IDSign").append(gre.getIssuerRuc()).append("</cbc:ID>\n");
        xml.append("    <cac:SignatoryParty>\n");
        xml.append("      <cac:PartyIdentification><cbc:ID>").append(gre.getIssuerRuc()).append("</cbc:ID></cac:PartyIdentification>\n");
        xml.append("      <cac:PartyName><cbc:Name><![CDATA[").append(gre.getIssuerName()).append("]]></cbc:Name></cac:PartyName>\n");
        xml.append("    </cac:SignatoryParty>\n");
        xml.append("    <cac:DigitalSignatureAttachment>\n");
        xml.append("      <cac:ExternalReference><cbc:URI>#SignatureValue</cbc:URI></cac:ExternalReference>\n");
        xml.append("    </cac:DigitalSignatureAttachment>\n");
        xml.append("  </cac:Signature>\n");

        // Despatch supplier (issuer)
        xml.append("  <cac:DespatchSupplierParty>\n");
        xml.append("    <cbc:CustomerAssignedAccountID schemeID=\"6\">").append(gre.getIssuerRuc()).append("</cbc:CustomerAssignedAccountID>\n");
        xml.append("    <cac:Party>\n");
        xml.append("      <cac:PartyLegalEntity>\n");
        xml.append("        <cbc:RegistrationName><![CDATA[").append(gre.getIssuerName()).append("]]></cbc:RegistrationName>\n");
        xml.append("      </cac:PartyLegalEntity>\n");
        xml.append("    </cac:Party>\n");
        xml.append("  </cac:DespatchSupplierParty>\n");

        // Delivery customer (receiver)
        xml.append("  <cac:DeliveryCustomerParty>\n");
        xml.append("    <cbc:CustomerAssignedAccountID schemeID=\"").append(gre.getReceiverDocType()).append("\">")
            .append(gre.getReceiverDoc()).append("</cbc:CustomerAssignedAccountID>\n");
        xml.append("    <cac:Party>\n");
        xml.append("      <cac:PartyLegalEntity>\n");
        xml.append("        <cbc:RegistrationName><![CDATA[").append(gre.getReceiverName()).append("]]></cbc:RegistrationName>\n");
        xml.append("      </cac:PartyLegalEntity>\n");
        xml.append("    </cac:Party>\n");
        xml.append("  </cac:DeliveryCustomerParty>\n");

        // Shipment
        xml.append("  <cac:Shipment>\n");
        xml.append("    <cbc:ID>1</cbc:ID>\n");
        xml.append("    <cbc:HandlingCode>").append(gre.getTransferReasonCode()).append("</cbc:HandlingCode>\n");
        if (gre.getTransferReasonDesc() != null) {
            xml.append("    <cbc:Information><![CDATA[").append(gre.getTransferReasonDesc()).append("]]></cbc:Information>\n");
        }

        // Gross weight
        if (gre.getGrossWeight() != null) {
            xml.append("    <cbc:GrossWeightMeasure unitCode=\"").append(gre.getWeightUnit()).append("\">")
                .append(gre.getGrossWeight()).append("</cbc:GrossWeightMeasure>\n");
        }
        if (gre.getPackageCount() != null) {
            xml.append("    <cbc:TotalTransportHandlingUnitQuantity>").append(gre.getPackageCount()).append("</cbc:TotalTransportHandlingUnitQuantity>\n");
        }

        // Shipment stage (transport)
        xml.append("    <cac:ShipmentStage>\n");
        xml.append("      <cbc:TransportModeCode>").append(gre.getTransportMode()).append("</cbc:TransportModeCode>\n");
        xml.append("      <cac:TransitPeriod>\n");
        xml.append("        <cbc:StartDate>").append(gre.getStartDate().format(DATE_FMT)).append("</cbc:StartDate>\n");
        xml.append("      </cac:TransitPeriod>\n");

        // Carrier (public transport - mode 01)
        if ("01".equals(gre.getTransportMode()) && gre.getCarrierRuc() != null) {
            xml.append("      <cac:CarrierParty>\n");
            xml.append("        <cac:PartyIdentification><cbc:ID schemeID=\"6\">").append(gre.getCarrierRuc()).append("</cbc:ID></cac:PartyIdentification>\n");
            xml.append("        <cac:PartyName><cbc:Name><![CDATA[").append(gre.getCarrierName()).append("]]></cbc:Name></cac:PartyName>\n");
            xml.append("      </cac:CarrierParty>\n");
        }

        // Driver (private transport - mode 02)
        if ("02".equals(gre.getTransportMode()) && gre.getDriverDoc() != null) {
            xml.append("      <cac:DriverPerson>\n");
            xml.append("        <cbc:ID schemeID=\"").append(gre.getDriverDocType()).append("\">").append(gre.getDriverDoc()).append("</cbc:ID>\n");
            xml.append("        <cbc:FirstName><![CDATA[").append(gre.getDriverName()).append("]]></cbc:FirstName>\n");
            if (gre.getDriverLicense() != null) {
                xml.append("        <cac:IdentityDocumentReference><cbc:ID>").append(gre.getDriverLicense()).append("</cbc:ID></cac:IdentityDocumentReference>\n");
            }
            xml.append("      </cac:DriverPerson>\n");
        }

        xml.append("    </cac:ShipmentStage>\n");

        // Delivery (origin + destination)
        xml.append("    <cac:Delivery>\n");
        xml.append("      <cac:DeliveryAddress>\n");
        if (gre.getDestinationUbigeo() != null) xml.append("        <cbc:ID>").append(gre.getDestinationUbigeo()).append("</cbc:ID>\n");
        if (gre.getDestinationAddress() != null) xml.append("        <cac:AddressLine><cbc:Line><![CDATA[").append(gre.getDestinationAddress()).append("]]></cbc:Line></cac:AddressLine>\n");
        xml.append("      </cac:DeliveryAddress>\n");
        xml.append("      <cac:Despatch>\n");
        xml.append("        <cac:DespatchAddress>\n");
        if (gre.getOriginUbigeo() != null) xml.append("          <cbc:ID>").append(gre.getOriginUbigeo()).append("</cbc:ID>\n");
        if (gre.getOriginAddress() != null) xml.append("          <cac:AddressLine><cbc:Line><![CDATA[").append(gre.getOriginAddress()).append("]]></cbc:Line></cac:AddressLine>\n");
        xml.append("        </cac:DespatchAddress>\n");
        xml.append("      </cac:Despatch>\n");
        xml.append("    </cac:Delivery>\n");

        // Vehicle (private transport)
        if ("02".equals(gre.getTransportMode()) && gre.getVehiclePlate() != null) {
            xml.append("    <cac:TransportHandlingUnit>\n");
            xml.append("      <cac:TransportEquipment>\n");
            xml.append("        <cbc:ID>").append(gre.getVehiclePlate()).append("</cbc:ID>\n");
            xml.append("      </cac:TransportEquipment>\n");
            xml.append("    </cac:TransportHandlingUnit>\n");
        }

        // Related document
        if (gre.getRelatedDocNumber() != null) {
            xml.append("    <cac:OriginAddress>\n");
            xml.append("      <!-- Related doc reference -->\n");
            xml.append("    </cac:OriginAddress>\n");
        }

        xml.append("  </cac:Shipment>\n");

        // Despatch lines (items)
        for (DispatchGuideItem item : gre.getItems()) {
            xml.append("  <cac:DespatchLine>\n");
            xml.append("    <cbc:ID>").append(item.getLineNumber()).append("</cbc:ID>\n");
            xml.append("    <cbc:DeliveredQuantity unitCode=\"").append(item.getUnitCode()).append("\">")
                .append(item.getQuantity()).append("</cbc:DeliveredQuantity>\n");
            xml.append("    <cac:OrderLineReference><cbc:LineID>").append(item.getLineNumber()).append("</cbc:LineID></cac:OrderLineReference>\n");
            xml.append("    <cac:Item>\n");
            xml.append("      <cbc:Description><![CDATA[").append(item.getProductName()).append("]]></cbc:Description>\n");
            if (item.getProductCode() != null) {
                xml.append("      <cac:SellersItemIdentification><cbc:ID>").append(item.getProductCode()).append("</cbc:ID></cac:SellersItemIdentification>\n");
            }
            xml.append("    </cac:Item>\n");
            xml.append("  </cac:DespatchLine>\n");
        }

        xml.append("</DespatchAdvice>");
        return xml.toString();
    }
}
