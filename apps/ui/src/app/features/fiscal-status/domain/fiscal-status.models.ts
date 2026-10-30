import type { FiscalReadinessCheck } from '../../config/public-api';
export interface FiscalStatus {
  businessCode: string;
  businessName: string;
  environment: string;
  sendMode: string;
  ready: boolean;
  blockers: number;
  checks: FiscalReadinessCheck[];
  certificate: {
    state: 'VALID' | 'EXPIRING' | 'CRITICAL' | 'EXPIRED' | 'MISSING';
    alias: string | null;
    validFrom: string | null;
    validTo: string | null;
    daysRemaining: number | null;
    stored: boolean;
    verificationLevel: string;
    limitation: string;
  };
  lastSignedAt: string | null;
  lastAcceptedAt: string | null;
  checkedAt: string;
}
