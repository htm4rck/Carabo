import { describe, expect, it } from 'vitest';
import { fiscalResponse } from './fiscal-response';

describe('Fiscal rejection response', () => {
  it('shows the actual SUNAT code and separates the diagnostic from the reason', () => {
    const response = fiscalResponse({
      status: 'REJECTED',
      sunatResponse:
        'El valor unitario no cumple el formato - Detalle: ticket: 1793546058930 error: Error en la linea: 1: 2369 (nodo: "cbc:PriceAmount" valor: "66.94915254237289")',
    });
    expect(response?.code).toBe('2369');
    expect(response?.message).toBe('El valor unitario no cumple el formato');
    expect(response?.detail).toContain('66.94915254237289');
  });
  it('keeps a response with no detail and explains an absent reason', () => {
    expect(
      fiscalResponse({ status: 'ERROR', sunatResponse: 'Error 1033: documento inválido' })?.code,
    ).toBe('1033');
    expect(fiscalResponse({ status: 'REJECTED', sunatResponse: '' })?.message).toContain(
      'No se registró el motivo',
    );
    expect(fiscalResponse({ status: 'ACCEPTED', sunatResponse: 'Aceptado' })).toBeNull();
  });
});
