import type { DocumentSummary } from '../domain/documents.models';

export interface FiscalResponse {
  code: string | null;
  message: string;
  detail: string | null;
}

export function fiscalResponse(
  document: Pick<DocumentSummary, 'status' | 'sunatResponse'>,
): FiscalResponse | null {
  if (
    !['REJECTED', 'ERROR', 'BLOCKED_CONFIGURATION', 'OBSERVED', 'FAILED'].includes(document.status)
  )
    return null;
  const raw = document.sunatResponse?.trim();
  if (!raw)
    return {
      code: null,
      message: 'No se registró el motivo. Consulta el estado del comprobante.',
      detail: null,
    };
  const code =
    /error\s+en\s+la\s+l[ií]nea\s*:\s*\d+\s*:\s*(\d{4})\b/i.exec(raw)?.[1] ??
    /(?:c[oó]digo|error)\s*[:#-]?\s*(\d{4})\b/i.exec(raw)?.[1] ??
    null;
  const parts = raw.split(/\s*[-–]\s*Detalle\s*:\s*/i);
  return { code, message: parts[0], detail: parts.length > 1 ? parts.slice(1).join(' · ') : null };
}
