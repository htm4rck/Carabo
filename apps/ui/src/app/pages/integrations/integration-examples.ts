export type IntegrationDocumentType = '01' | '03' | '07' | '08';

export const DOCUMENT_TYPE_LABELS: Record<IntegrationDocumentType, string> = {
  '01': 'Factura',
  '03': 'Boleta',
  '07': 'Nota de crédito',
  '08': 'Nota de débito'
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
