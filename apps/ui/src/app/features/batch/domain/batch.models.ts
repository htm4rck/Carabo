export interface BatchResult {
  voidCommunication?: boolean;
  id: string | null;
  kind: 'RC' | 'RA' | null;
  identifier: string | null;
  success: boolean;
  ticket: string | null;
  status: 'EMPTY' | 'SENT' | 'PENDING' | 'ACCEPTED' | 'OBSERVED' | 'REJECTED' | 'ERROR' | string;
  message: string;
  documentCount: number;
  referenceDate: string | null;
  generatedAt: string;
  timeZone: string;
}
