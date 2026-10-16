export type IntegrationDocumentType = '01' | '03' | '07' | '08' | '09';

export const DOCUMENT_TYPE_LABELS: Record<IntegrationDocumentType, string> = {
  '01': 'Factura',
  '03': 'Boleta',
  '07': 'Nota de crédito',
  '08': 'Nota de débito',
  '09': 'Guía de remisión remitente'
};

function limaDate(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `${value['year']}-${value['month']}-${value['day']}`;
}

export function integrationExample(type: IntegrationDocumentType): Record<string, unknown> {
  const date = limaDate();
  const externalId = `erp-${type}-${date.replaceAll('-', '')}-00018452`;
  if (type === '09') {
    return {
      series: 'T001',
      issueDate: date,
      startDate: date,
      receiver: { docType: '6', doc: '20131312955', name: 'CLIENTE EMPRESA SAC' },
      transportMode: '02',
      grossWeight: 120.5,
      weightUnit: 'KGM',
      packageCount: 4,
      origin: { ubigeo: '150101', address: 'Av. Industrial 123, Lima' },
      destination: { ubigeo: '040101', address: 'Av. Logística 456, Arequipa' },
      driver: { docType: '1', doc: '12345678', name: 'ANA TORRES', license: 'Q12345678' },
      vehiclePlate: 'ABC123',
      transferReasonCode: '01',
      transferReasonDesc: 'Venta',
      relatedDoc: { docType: '01', number: 'F001-18452' },
      items: [{
        productCode: 'PROD-001',
        productName: 'Producto de prueba',
        unitCode: 'NIU',
        quantity: 4,
        weight: 120.5
      }],
      callbackUrl: 'https://erp.example.com/webhooks/carabo',
      externalId
    };
  }
  const receipt = type === '03';
  const payload: Record<string, unknown> = {
    docType: type,
    series: receipt ? 'B001' : 'F001',
    issueDate: date,
    currency: 'PEN',
    externalId,
    sourceDocId: 'VENTA-00018452',
    receiver: receipt
      ? { docType: '1', doc: '12345678', name: 'CLIENTE DE PRUEBA', address: 'Lima, Perú' }
      : { docType: '6', doc: '20131312955', name: 'CLIENTE EMPRESA SAC', address: 'Lima, Perú' },
    lines: [{
      productCode: 'SERV-001',
      productName: 'Servicio mensual de ejemplo',
      unitCode: 'NIU',
      quantity: 1,
      unitPrice: 100.00,
      discount: 0,
      affectationCode: '10'
    }]
  };
  if (type === '07' || type === '08') {
    payload['reference'] = {
      docType: '01', series: 'F001', number: 123,
      reasonCode: type === '07' ? '01' : '02',
      reason: type === '07' ? 'Anulación de la operación' : 'Aumento en el valor'
    };
  }
  return payload;
}

export function exampleExternalId(type: IntegrationDocumentType): string {
  return String(integrationExample(type)['externalId']);
}
